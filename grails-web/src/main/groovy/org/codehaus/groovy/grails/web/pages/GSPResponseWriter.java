/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.web.pages;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.support.encoding.Encoder;
import org.codehaus.groovy.grails.support.encoding.EncoderAware;
import org.codehaus.groovy.grails.support.encoding.EncodingStateRegistry;
import org.codehaus.groovy.grails.support.encoding.EncodingStateRegistryLookup;
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest;
import org.codehaus.groovy.grails.web.sitemesh.GrailsContentBufferingResponse;
import org.codehaus.groovy.grails.web.sitemesh.GrailsRoutablePrintWriter;
import org.codehaus.groovy.grails.web.util.BoundedCharsAsEncodedBytesCounter;
import org.codehaus.groovy.grails.web.util.GrailsPrintWriterAdapter;
import org.codehaus.groovy.grails.web.util.StreamCharBuffer;
import org.codehaus.groovy.grails.web.util.StreamCharBuffer.LazyInitializingWriter;
import org.codehaus.groovy.grails.web.util.StreamCharBuffer.StreamCharBufferWriter;

import com.opensymphony.module.sitemesh.RequestConstants;

/**
 * NOTE: Based on work done by on the GSP standalone project (https://gsp.dev.java.net/)
 *
 * A buffered writer that won't commit the response until the buffer has reached the high
 * water mark, or until flush() or close() is called.
 *
 * Performance optimizations by Lari Hotari, 13.03.2009
 *
 * Calculating the Content-Length has been disabled by default since Jetty ignores it (uses Chunked mode anyways).
 * Content-Length mode can be enabled with -DGSPResponseWriter.enableContentLength=true system property.
 *
 *
 * @author Troy Heninger
 * @author Graeme Rocher
 * @author Lari Hotari, Sagire Software Oy
 *
 * Date: Jan 10, 2004
 */
public class GSPResponseWriter extends GrailsPrintWriterAdapter {
    protected static final Log LOG = LogFactory.getLog(GSPResponseWriter.class);
    private ServletResponse response;
    private BoundedCharsAsEncodedBytesCounter bytesCounter;
    public static final boolean CONTENT_LENGTH_COUNTING_ENABLED = Boolean.getBoolean("GSPResponseWriter.enableContentLength");
    public static final boolean BUFFERING_ENABLED = Boolean.valueOf(System.getProperty("GSPResponseWriter.enableBuffering","true"));
    public static final boolean AUTOFLUSH_ENABLED = Boolean.getBoolean("GSPResponseWriter.enableAutoFlush");
    private static final int BUFFER_SIZE = Integer.getInteger("GSPResponseWriter.bufferSize", 8042);

    public static GSPResponseWriter getInstance(final ServletResponse response) {
        return getInstance(response, BUFFER_SIZE);
    }

    /**
     * Static factory methdirectWritingod to create the writer.
     * @param response
     * @param max
     * @return  A GSPResponseWriter instance
     */
    private static GSPResponseWriter getInstance(final ServletResponse response, final int max) {
        Writer target = null;
        final BoundedCharsAsEncodedBytesCounter bytesCounter=new BoundedCharsAsEncodedBytesCounter();

        if (!(response instanceof GrailsContentBufferingResponse) && (BUFFERING_ENABLED || CONTENT_LENGTH_COUNTING_ENABLED)) {
            final StreamCharBuffer streamBuffer = new StreamCharBuffer(max, 0, max);
            streamBuffer.setChunkMinSize(max/2);
            target = streamBuffer.getWriter();
            
            final StreamCharBuffer.LazyInitializingWriter lazyResponseWriter = new StreamCharBuffer.LazyInitializingWriter() {
                public Writer getWriter() throws IOException {
                    return response.getWriter();
                }
            };
            
            
            streamBuffer.connectTo(new StreamCharBuffer.LazyInitializingMultipleWriter() {
                public Writer getWriter() throws IOException {
                    return null;
                }

                public LazyInitializingWriter[] initializeMultiple(boolean autoFlush) throws IOException {
                    final GrailsWebRequest webRequest = GrailsWebRequest.lookup();
                    final Encoder encoder = webRequest != null ? webRequest.lookupFilteringEncoder() : null;
                    final StreamCharBuffer.LazyInitializingWriter[] lazyWriters;
                    if (CONTENT_LENGTH_COUNTING_ENABLED) {
                        lazyWriters=new StreamCharBuffer.LazyInitializingWriter[] {new StreamCharBuffer.LazyInitializingWriter() {
                            public Writer getWriter() throws IOException {
                                bytesCounter.setCapacity(max * 2);
                                bytesCounter.setEncoding(response.getCharacterEncoding());
                                return bytesCounter.getCountingWriter();
                            }
                        }, lazyResponseWriter};
                    } else {
                        lazyWriters=new StreamCharBuffer.LazyInitializingWriter[] {lazyResponseWriter};
                    }
                    if(encoder != null) {
                        return new StreamCharBuffer.LazyInitializingWriter[] {streamBuffer.createEncodingInitializer(new EncoderAware() {
                            public Encoder getEncoder() {
                                return encoder;
                            }
                        }, new EncodingStateRegistryLookup() {
                            public EncodingStateRegistry lookup() {
                                return webRequest.getEncodingStateRegistry();
                            } }, lazyWriters)};
                    } else {
                        return lazyWriters;
                    }
                }
            }, AUTOFLUSH_ENABLED);
        }
        else {
            try {
                target = response.getWriter();
            }
            catch (IOException e) {
                LOG.error("Problem getting writer from response",e);
                throw new RuntimeException("Problem getting writer from response",e);
            }
        }
        return new GSPResponseWriter(target, response, bytesCounter);
    }

    /**
     * Static factory method to create the writer.
     *
     * TODO: this can be removed?
     *
     * @param target The target writer to write too
     * @param max
     * @return  A GSPResponseWriter instance
     */
    @SuppressWarnings("unused")
    private static GSPResponseWriter getInstance(Writer target, int max) {
        if (BUFFERING_ENABLED && !(target instanceof GrailsRoutablePrintWriter) && !(target instanceof StreamCharBufferWriter)) {
            StreamCharBuffer streamBuffer=new StreamCharBuffer(max, 0, max);
            streamBuffer.connectTo(target, false);
            Writer writer=streamBuffer.getWriter();
            return new GSPResponseWriter(writer);
        }

        return new GSPResponseWriter(target);
    }

    /**
     * Private constructor.  Use getInstance() instead.
     * @param activeWriter buffered writer
     * @param response
     * @param streamBuffer StreamCharBuffer instance
     * @param bytesCounter    Keeps count of encoded bytes count
     */
    private GSPResponseWriter(Writer activeWriter, final ServletResponse response, BoundedCharsAsEncodedBytesCounter bytesCounter) {
        super(activeWriter);
        this.response = response;
        this.bytesCounter = bytesCounter;
    }

    /**
     * Private constructor.  Use getInstance() instead.
     * @param activeWriter buffered writer
     */
    private GSPResponseWriter(Writer activeWriter) {
        super(activeWriter);
    }

    /**
     * Close the stream.
     * @see #checkError()
     */
    @Override
    public void close() {
        flush();
        if (canFlushContentLengthAwareResponse()) {
            int size = bytesCounter.size();
            if (size > 0) {
                response.setContentLength(size);
            }
            flushResponse();
        }
        else if (!isTrouble()) {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup();
            if (webRequest != null && webRequest.getCurrentRequest().getAttribute(RequestConstants.PAGE) != null) {
                // flush the response if its a layout
                flushResponse();
            }
        }
    }

    private boolean canFlushContentLengthAwareResponse() {
        return CONTENT_LENGTH_COUNTING_ENABLED && bytesCounter != null && bytesCounter.isWriterReferenced() && response != null && !response.isCommitted() && !isTrouble();
    }

    private void flushResponse() {
        try {
            response.getWriter().flush();
        }
        catch (IOException e) {
            handleIOException(e);
        }
    }
}
