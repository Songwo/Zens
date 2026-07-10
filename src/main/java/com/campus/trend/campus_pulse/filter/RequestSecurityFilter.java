package com.campus.trend.campus_pulse.filter;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.config.SecurityWhitelist;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
public class RequestSecurityFilter extends OncePerRequestFilter {

    private static final long TIMESTAMP_WINDOW_MS = 5 * 60 * 1000L;
    private static final Pattern NONCE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{8,64}$");
    private static final Set<String> SKIP_PATHS = Set.of(
            "/auth/login",
            "/auth/register",
            "/auth/send-code",
            "/auth/verify-code",
            "/auth/check-email",
            "/auth/check-username",
            "/auth/refresh",
            "/auth/reset-password",
            "/auth/github/authorize-url",
            "/auth/github/login",
            "/auth/2fa/verify-login",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/send-code",
            "/api/auth/verify-code",
            "/api/auth/check-email",
            "/api/auth/check-username",
            "/api/auth/refresh",
            "/api/auth/reset-password",
            "/api/auth/github/authorize-url",
            "/api/auth/github/login",
            "/api/auth/2fa/verify-login",
            "/performance/web-vitals",
            "/api/performance/web-vitals");
    private static final Set<String> PUBLIC_MUTATING_PATHS =
            new HashSet<>(Arrays.asList(SecurityWhitelist.PUBLIC_POST_URLS));

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final boolean signatureRequired;

    public RequestSecurityFilter(StringRedisTemplate redisTemplate, JwtUtil jwtUtil, ObjectMapper objectMapper,
            boolean signatureRequired) {
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.signatureRequired = signatureRequired;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isMutatingRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestUri = request.getRequestURI();
        if (SKIP_PATHS.contains(requestUri) || PUBLIC_MUTATING_PATHS.contains(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = jwtUtil.getToken(request);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = jwtUtil.getUserId(token);
        if (!StringUtils.hasText(userId)) {
            writeError(response, HttpStatus.UNAUTHORIZED.value(), "无效令牌上下文");
            return;
        }

        String timestampHeader = request.getHeader("X-Request-Timestamp");
        String nonce = request.getHeader("X-Request-Nonce");
        String signature = request.getHeader("X-Request-Signature");
        String deviceId = request.getHeader("X-Device-Id");

        if (!StringUtils.hasText(timestampHeader) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            if (!signatureRequired) {
                log.debug("请求签名未提供，兼容放行: method={}, uri={}, userId={}",
                        request.getMethod(), requestUri, userId);
                filterChain.doFilter(request, response);
                return;
            }
            log.warn("缺少安全请求头: method={}, uri={}, userId={}",
                    request.getMethod(), requestUri, userId);
            writeError(response, HttpStatus.BAD_REQUEST.value(), "缺少安全请求头");
            return;
        }
        if (!NONCE_PATTERN.matcher(nonce).matches()) {
            log.warn("请求随机串不合法: method={}, uri={}, userId={}, nonce={}",
                    request.getMethod(), requestUri, userId, nonce);
            writeError(response, HttpStatus.BAD_REQUEST.value(), "请求随机串不合法");
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (Exception e) {
            log.warn("请求时间戳不合法: method={}, uri={}, userId={}, ts={}",
                    request.getMethod(), requestUri, userId, timestampHeader);
            writeError(response, HttpStatus.BAD_REQUEST.value(), "请求时间戳不合法");
            return;
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > TIMESTAMP_WINDOW_MS) {
            log.warn("请求时间戳过期: method={}, uri={}, userId={}, ts={}, now={}",
                    request.getMethod(), requestUri, userId, timestamp, now);
            writeError(response, HttpStatus.UNAUTHORIZED.value(), "请求已过期，请刷新页面重试");
            return;
        }

        String nonceKey = "auth:req:nonce:" + userId + ":" + nonce;
        Boolean nonceOk = redisTemplate.opsForValue().setIfAbsent(nonceKey, "1", TIMESTAMP_WINDOW_MS, TimeUnit.MILLISECONDS);
        if (!Boolean.TRUE.equals(nonceOk)) {
            log.warn("请求随机串复用: method={}, uri={}, userId={}, nonce={}",
                    request.getMethod(), requestUri, userId, nonce);
            writeError(response, HttpStatus.CONFLICT.value(), "疑似重复请求，请稍后重试");
            return;
        }

        if (!matchesSignature(request.getMethod(), requestUri, timestampHeader, nonce, deviceId, token, signature)) {
            log.warn("请求签名校验失败: method={}, uri={}, userId={}",
                    request.getMethod(), requestUri, userId);
            writeError(response, HttpStatus.UNAUTHORIZED.value(), "请求签名校验失败");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isMutatingRequest(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private String buildSignature(String method, String uri, String timestamp, String nonce, String deviceId, String token) {
        String payload = method + "\n"
                + uri + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + (deviceId == null ? "" : deviceId) + "\n"
                + token;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean matchesSignature(String method, String requestUri, String timestamp, String nonce, String deviceId,
            String token, String signature) {
        if (constantTimeEquals(buildSignature(method, requestUri, timestamp, nonce, deviceId, token), signature)) {
            return true;
        }
        String apiStrippedUri = stripApiPrefix(requestUri);
        return !requestUri.equals(apiStrippedUri)
                && constantTimeEquals(buildSignature(method, apiStrippedUri, timestamp, nonce, deviceId, token), signature);
    }

    private String stripApiPrefix(String requestUri) {
        if (requestUri != null && requestUri.startsWith("/api/")) {
            return requestUri.substring(4);
        }
        return requestUri;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(ResultCode.TOKEN_INVALID, message)));
    }
}
