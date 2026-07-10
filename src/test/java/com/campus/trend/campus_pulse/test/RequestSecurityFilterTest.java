package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.config.SecurityWhitelist;
import com.campus.trend.campus_pulse.filter.RequestSecurityFilter;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class RequestSecurityFilterTest {

    @ParameterizedTest
    @MethodSource("publicPostUris")
    void shouldBypassSignatureCheckForPublicPostPaths(String uri) throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        RequestSecurityFilter filter =
                new RequestSecurityFilter(redisTemplate, jwtUtil, new ObjectMapper(), true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.addHeader("Authorization", "Bearer test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNotNull(filterChain.getRequest());
        verifyNoInteractions(jwtUtil, redisTemplate);
    }

    @ParameterizedTest
    @MethodSource("apiPrefixedMutatingUris")
    void shouldAcceptSignatureBuiltWithoutApiPrefix(String requestUri, String signedUri) throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), eq("1"), anyLong(), any())).thenReturn(true);

        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.getToken(any())).thenReturn("test-token");
        when(jwtUtil.getUserId("test-token")).thenReturn("42");
        RequestSecurityFilter filter =
                new RequestSecurityFilter(redisTemplate, jwtUtil, new ObjectMapper(), true);

        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "abc123xyz";
        String deviceId = "device-1";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", requestUri);
        request.addHeader("Authorization", "Bearer test-token");
        request.addHeader("X-Device-Id", deviceId);
        request.addHeader("X-Request-Timestamp", timestamp);
        request.addHeader("X-Request-Nonce", nonce);
        request.addHeader("X-Request-Signature",
                sha256Hex("POST\n" + signedUri + "\n" + timestamp + "\n" + nonce + "\n" + deviceId + "\ntest-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNotNull(filterChain.getRequest());
    }

    @ParameterizedTest
    @MethodSource("publicGetUris")
    void shouldKeepPublicGetBusinessUrisWhitelisted(String uri) {
        List<String> publicGetUrls = Arrays.asList(SecurityWhitelist.PUBLIC_GET_URLS);

        assertTrue(publicGetUrls.contains(uri));
    }

    static Stream<String> publicPostUris() {
        return Arrays.stream(SecurityWhitelist.PUBLIC_POST_URLS);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> apiPrefixedMutatingUris() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("/api/check-in", "/check-in"),
                org.junit.jupiter.params.provider.Arguments.of("/api/user/profile", "/user/profile")
        );
    }

    static Stream<String> publicGetUris() {
        return Stream.of(
                "/search/hot-keywords",
                "/api/search/hot-keywords",
                "/search/suggestions",
                "/api/search/suggestions",
                "/poll/by-post/**",
                "/api/poll/by-post/**"
        );
    }

    private static String sha256Hex(String payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
