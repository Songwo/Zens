package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.interceptor.MetricsInterceptor;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MetricsInterceptorTest {

    @Test
    void customMetricsDoNotReuseSpringHttpMetricName() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Timer.builder("http.server.requests")
                .tag("method", "GET")
                .tag("status", "200")
                .tag("uri", "/health")
                .tag("outcome", "SUCCESS")
                .tag("exception", "none")
                .tag("error", "none")
                .register(registry);
        MetricsInterceptor interceptor = new MetricsInterceptor(registry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/post/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertNotNull(registry.find("campus.http.server.duration").timer());
        assertEquals(1.0, registry.find("campus.http.server.requests").counter().count());
        assertEquals(1, registry.find("http.server.requests").meters().size());
    }
}
