package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SecurityConfigCorsTest {

    @Test
    void shouldAllowLocalOriginsWithoutPort() {
        SecurityConfig securityConfig = new SecurityConfig(new ObjectMapper());
        ReflectionTestUtils.setField(
                securityConfig,
                "allowedOriginPatterns",
                "http://localhost,http://127.0.0.1,http://localhost:5173,http://127.0.0.1:5173");

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/section/active");
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertNotNull(configuration);
        assertEquals("http://localhost", configuration.checkOrigin("http://localhost"));
        assertEquals("http://127.0.0.1", configuration.checkOrigin("http://127.0.0.1"));
        assertEquals("http://localhost:5173", configuration.checkOrigin("http://localhost:5173"));
        assertEquals("http://127.0.0.1:5173", configuration.checkOrigin("http://127.0.0.1:5173"));
    }
}
