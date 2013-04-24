package org.codehaus.groovy.grails.web.sitemesh;

import grails.util.GrailsWebUtil;
import grails.util.Holders;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;

import java.util.Collections;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;

import junit.framework.TestCase;

import org.codehaus.groovy.grails.commons.DefaultGrailsApplication;
import org.codehaus.groovy.grails.commons.GrailsApplication;
import org.codehaus.groovy.grails.plugins.MockGrailsPluginManager;
import org.codehaus.groovy.grails.support.MockApplicationContext;
import org.codehaus.groovy.grails.web.pages.DefaultGroovyPagesUriService;
import org.codehaus.groovy.grails.web.pages.GroovyPagesTemplateEngine;
import org.codehaus.groovy.grails.web.pages.GroovyPagesUriService;
import org.codehaus.groovy.grails.web.pages.discovery.GrailsConventionGroovyPageLocator;
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes;
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest;
import org.codehaus.groovy.grails.web.servlet.view.GrailsViewResolver;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.opensymphony.module.sitemesh.Config;
import com.opensymphony.module.sitemesh.Decorator;
import com.opensymphony.module.sitemesh.Page;
import com.opensymphony.module.sitemesh.parser.HTMLPageParser;

public class GrailsLayoutDecoratorMapperTests extends TestCase {

    private GrailsWebRequest buildMockRequest(ConfigObject config) throws Exception {
        MockApplicationContext appCtx = new MockApplicationContext();
        appCtx.registerMockBean(GroovyPagesUriService.BEAN_ID, new DefaultGroovyPagesUriService());

        DefaultGrailsApplication grailsApplication = new DefaultGrailsApplication();
        grailsApplication.setConfig(config);
        Holders.setConfig(config);
        appCtx.registerMockBean(GrailsApplication.APPLICATION_ID, grailsApplication);
        GrailsConventionGroovyPageLocator pageLocator = new GrailsConventionGroovyPageLocator();
        pageLocator.setApplicationContext(appCtx);

        GroovyPagesTemplateEngine gpte = new GroovyPagesTemplateEngine(appCtx.getServletContext());
        gpte.setApplicationContext(appCtx);
        gpte.afterPropertiesSet();

        GrailsViewResolver grailsViewResolver=new GrailsViewResolver();
        grailsViewResolver.setGrailsApplication(grailsApplication);
        grailsViewResolver.setApplicationContext(appCtx);
        grailsViewResolver.setGroovyPageLocator(pageLocator);
        grailsViewResolver.setPluginManager(new MockGrailsPluginManager(grailsApplication));
        grailsViewResolver.setTemplateEngine(gpte);

        GroovyPageLayoutFinder layoutFinder = new GroovyPageLayoutFinder();
        layoutFinder.setViewResolver(grailsViewResolver);
        @SuppressWarnings("rawtypes")
        Map flat = config != null ?  config.flatten() : Collections.emptyMap();
        layoutFinder.setDefaultDecoratorName(flat.get("grails.sitemesh.default.layout") != null ? flat.get("grails.sitemesh.default.layout").toString(): "application");

        appCtx.registerMockBean("groovyPageLocator", pageLocator);
        appCtx.registerMockBean("groovyPageLayoutFinder", layoutFinder);
        appCtx.getServletContext().setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, appCtx);
        appCtx.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appCtx);
        return GrailsWebUtil.bindMockWebRequest(appCtx, new MockHttpServletRequest(appCtx.getServletContext()) {
            @Override
            public RequestDispatcher getRequestDispatcher(String path) {
                return null;
            }
        }, new MockHttpServletResponse());
    }

    /*
     * Test method for 'org.codehaus.groovy.grails.web.sitemesh.GrailsLayoutDecoratorMapper.getDecorator(HttpServletRequest, Page)'
     */
    public void testGetDecoratorHttpServletRequestPage() throws Exception {
        GrailsWebRequest webRequest = buildMockRequest(null);
        webRequest.setAttribute(GrailsLayoutDecoratorMapper.RENDERING_VIEW, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        MockApplicationContext appCtx = (MockApplicationContext)webRequest.getApplicationContext();
        appCtx.registerMockResource("/grails-app/views/layouts/test.gsp", "<html><body><g:layoutBody /></body></html>");

        MockHttpServletRequest request = (MockHttpServletRequest)webRequest.getCurrentRequest();
        request.setMethod("GET");
        request.setRequestURI("orders/list");
        ServletContext context = webRequest.getServletContext();
        GrailsLayoutDecoratorMapper m = new GrailsLayoutDecoratorMapper();
        Config c = new Config(new MockServletConfig(context));
        m.init(c, null, null);
        HTMLPageParser parser = new HTMLPageParser();
        String html = "<html><head><title>Test title</title><meta name=\"layout\" content=\"test\"></meta></head><body>here is the body</body></html>";

        Page page = parser.parse(html.toCharArray());
        Decorator d = m.getDecorator(request, page);
        assertNotNull(d);
        assertEquals("/layouts/test.gsp", d.getPage());
        assertEquals("test", d.getName());
    }

    public void testDecoratedByApplicationConventionForViewsNotRenderedByAController() throws Exception {
        GrailsWebRequest webRequest = buildMockRequest(null);
        webRequest.setAttribute(GrailsLayoutDecoratorMapper.RENDERING_VIEW, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        MockApplicationContext appCtx = (MockApplicationContext)webRequest.getApplicationContext();
        appCtx.registerMockResource("/grails-app/views/layouts/application.gsp", "<html><body><h1>Default Layout</h1><g:layoutBody /></body></html>");

        MockHttpServletRequest request = (MockHttpServletRequest)webRequest.getCurrentRequest();
        request.setMethod("GET");
        request.setRequestURI("/");

        ServletContext context = webRequest.getServletContext();

        GrailsLayoutDecoratorMapper m = new GrailsLayoutDecoratorMapper();
        Config c = new Config(new MockServletConfig(context));
        m.init(c, null, null);
        HTMLPageParser parser = new HTMLPageParser();
        String html = "<html><head><title>Foo title</title></head><body>here is the body</body></html>";

        Page page = parser.parse(html.toCharArray());
        Decorator d = m.getDecorator(request, page);
        assertNotNull(d);
        assertEquals("/layouts/application.gsp", d.getPage());
        assertEquals("application", d.getName());
    }

    public void testDecoratedByApplicationConvention() throws Exception {
        GrailsWebRequest webRequest = buildMockRequest(null);
        webRequest.setAttribute(GrailsLayoutDecoratorMapper.RENDERING_VIEW, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        MockApplicationContext appCtx = (MockApplicationContext)webRequest.getApplicationContext();
        appCtx.registerMockResource("/grails-app/views/layouts/application.gsp", "<html><body><h1>Default Layout</h1><g:layoutBody /></body></html>");

        MockHttpServletRequest request = (MockHttpServletRequest)webRequest.getCurrentRequest();
        request.setMethod("GET");
        request.setRequestURI("orders/list");
        ServletContext context = webRequest.getServletContext();
        GroovyClassLoader gcl = new GroovyClassLoader();

        // create mock controller
        GroovyObject controller = (GroovyObject)gcl.parseClass("class FooController {\n" +
                "def controllerName = 'foo'\n" +
                "def actionUri = '/foo/fooAction'\n" +
        "}").newInstance();

        request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller);
        GrailsLayoutDecoratorMapper m = new GrailsLayoutDecoratorMapper();
        Config c = new Config(new MockServletConfig(context));
        m.init(c, null, null);
        HTMLPageParser parser = new HTMLPageParser();
        String html = "<html><head><title>Foo title</title></head><body>here is the body</body></html>";

        Page page = parser.parse(html.toCharArray());
        Decorator d = m.getDecorator(request, page);
        assertNotNull(d);
        assertEquals("/layouts/application.gsp", d.getPage());
        assertEquals("application", d.getName());
    }

    public void testOverridingDefaultTemplateViaConfig() throws Exception {
            ConfigObject config = new ConfigSlurper().parse("grails.sitemesh.default.layout='otherApplication'");
            GrailsWebRequest webRequest = buildMockRequest(config);
            webRequest.setAttribute(GrailsLayoutDecoratorMapper.RENDERING_VIEW, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
            MockApplicationContext appCtx = (MockApplicationContext)webRequest.getApplicationContext();
            appCtx.registerMockResource("/grails-app/views/layouts/application.gsp", "<html><body><h1>Default Layout</h1><g:layoutBody /></body></html>");
            appCtx.registerMockResource("/grails-app/views/layouts/otherApplication.gsp", "<html><body><h1>Other Default Layout</h1><g:layoutBody /></body></html>");

            MockHttpServletRequest request = (MockHttpServletRequest)webRequest.getCurrentRequest();
            request.setMethod("GET");
            request.setRequestURI("orders/list");
            ServletContext context = webRequest.getServletContext();
            GroovyClassLoader gcl = new GroovyClassLoader();

            // create mock controller
            GroovyObject controller = (GroovyObject)gcl.parseClass("class FooController {\n" +
                    "def controllerName = 'foo'\n" +
                    "def actionUri = '/foo/fooAction'\n" +
            "}").newInstance();

            request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller);
            GrailsLayoutDecoratorMapper m = new GrailsLayoutDecoratorMapper();
            Config c = new Config(new MockServletConfig(context));
            m.init(c, null, null);
            HTMLPageParser parser = new HTMLPageParser();
            String html = "<html><head><title>Foo title</title></head><body>here is the body</body></html>";

            Page page = parser.parse(html.toCharArray());
            Decorator d = m.getDecorator(request, page);
            assertNotNull(d);
            assertEquals("/layouts/otherApplication.gsp", d.getPage());
            assertEquals("otherApplication", d.getName());
    }

    public void testDecoratedByControllerConvention() throws Exception {
        GrailsWebRequest webRequest = buildMockRequest(null);
        webRequest.setAttribute(GrailsLayoutDecoratorMapper.RENDERING_VIEW, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        MockApplicationContext appCtx = (MockApplicationContext)webRequest.getApplicationContext();
        appCtx.registerMockResource("/grails-app/views/layouts/test.gsp", "<html><body><g:layoutBody /></body></html>");

        MockHttpServletRequest request = (MockHttpServletRequest)webRequest.getCurrentRequest();
        request.setMethod("GET");
        request.setRequestURI("orders/list");
        ServletContext context = webRequest.getServletContext();
        GroovyClassLoader gcl = new GroovyClassLoader();

        // create mock controller
        GroovyObject controller = (GroovyObject)gcl.parseClass("class TestController {\n" +
                "def controllerName = 'test'\n" +
                "def actionUri = '/test/testAction'\n" +
        "}").newInstance();

        request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller);
        GrailsLayoutDecoratorMapper m = new GrailsLayoutDecoratorMapper();
        Config c = new Config(new MockServletConfig(context));
        m.init(c, null, null);
        HTMLPageParser parser = new HTMLPageParser();
        String html = "<html><head><title>Test title</title></head><body>here is the body</body></html>";

        Page page = parser.parse(html.toCharArray());
        Decorator d = m.getDecorator(request, page);
        assertNotNull(d);
        assertEquals("/layouts/test.gsp", d.getPage());
        assertEquals("test", d.getName());
    }

    public void testDecoratedByActionConvention() throws Exception {
        GrailsWebRequest webRequest = buildMockRequest(null);
        webRequest.setAttribute(GrailsLayoutDecoratorMapper.RENDERING_VIEW, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        MockApplicationContext appCtx = (MockApplicationContext)webRequest.getApplicationContext();
        appCtx.registerMockResource("/grails-app/views/layouts/test2/testAction.gsp", "<html><body><g:layoutBody /></body></html>");

        MockHttpServletRequest request = (MockHttpServletRequest)webRequest.getCurrentRequest();
        request.setMethod("GET");
        request.setRequestURI("orders/list");
        ServletContext context = webRequest.getServletContext();
        GroovyClassLoader gcl = new GroovyClassLoader();

        // create mock controller
        GroovyObject controller = (GroovyObject)gcl.parseClass("class Test2Controller {\n" +
                "def controllerName = 'test2'\n" +
                "def actionUri = '/test2/testAction'\n" +
        "}").newInstance();

        request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller);        GrailsLayoutDecoratorMapper m = new GrailsLayoutDecoratorMapper();
        Config c = new Config(new MockServletConfig(context));
        m.init(c, null, null);
        HTMLPageParser parser = new HTMLPageParser();
        String html = "<html><head><title>Test title</title></head><body>here is the body</body></html>";

        Page page = parser.parse(html.toCharArray());
        Decorator d = m.getDecorator(request, page);
        assertNotNull(d);
        assertEquals("/layouts/test2/testAction.gsp", d.getPage());
        assertEquals("test2/testAction", d.getName());
    }

    public void testDecoratedByLayoutPropertyInController() throws Exception {
        GrailsWebRequest webRequest = buildMockRequest(null);
        webRequest.setAttribute(GrailsLayoutDecoratorMapper.RENDERING_VIEW, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        MockApplicationContext appCtx = (MockApplicationContext)webRequest.getApplicationContext();
        appCtx.registerMockResource("/grails-app/views/layouts/test.gsp", "<html><body><g:layoutBody /></body></html>");
        appCtx.registerMockResource("/grails-app/views/layouts/mylayout.gsp", "<html><body><g:layoutBody /></body></html>");

        MockHttpServletRequest request = (MockHttpServletRequest)webRequest.getCurrentRequest();
        request.setMethod("GET");
        request.setRequestURI("orders/list");
        ServletContext context = webRequest.getServletContext();
        GroovyClassLoader gcl = new GroovyClassLoader();

        // create mock controller
        GroovyObject controller = (GroovyObject)gcl.parseClass("class TestController {\n" +
                "def controllerName = 'test'\n" +
                "def actionUri = '/test/testAction'\n" +
                "static layout = 'mylayout'\n" +
        "}").newInstance();

        request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller);
        GrailsLayoutDecoratorMapper m = new GrailsLayoutDecoratorMapper();
        Config c = new Config(new MockServletConfig(context));
        m.init(c, null, null);
        HTMLPageParser parser = new HTMLPageParser();
        String html = "<html><head><title>Test title</title></head><body>here is the body</body></html>";

        Page page = parser.parse(html.toCharArray());
        Decorator d = m.getDecorator(request, page);
        assertNotNull(d);
        assertEquals("/layouts/mylayout.gsp", d.getPage());
        assertEquals("mylayout", d.getName());
    }

    @Override
    protected void tearDown() {
        RequestContextHolder.setRequestAttributes(null);
    }
}
