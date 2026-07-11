package com.campus.trend.campus_pulse.filter;

import com.campus.trend.campus_pulse.common.api.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 服务到服务 HMAC 校验过滤器，仅作用于 /api/internal/**
 *
 * 头部 (必填):
 *   X-Service-Id          : 调用方标识 (必须命中白名单)
 *   X-Service-Timestamp   : 毫秒时间戳, 与服务器时钟差距 < TIMESTAMP_WINDOW_MS
 *   X-Service-Nonce       : 24~64 字符随机串, Redis 防重 (TTL = 时间窗口)
 *   X-Service-Signature   : HMAC_SHA256(secret, METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + sha256Hex(BODY))
 *
 * 失败一律返回 401 JSON, 不进入业务层。
 */
@Slf4j
@Component
public class InternalServiceFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PREFIX = "/api/internal/";
    private static final long TIMESTAMP_WINDOW_MS = 60_000L; // ±60s
    private static final Pattern NONCE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final com.campus.trend.campus_pulse.monitor.EcosystemMetrics ecosystemMetrics;
    /** serviceId -> 共享密钥。白名单即本 map 的 keySet,来自 internal.service.clients 配置。 */
    private final Map<String, String> serviceSecrets;

    public InternalServiceFilter(StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 com.campus.trend.campus_pulse.monitor.EcosystemMetrics ecosystemMetrics,
                                 com.campus.trend.campus_pulse.config.properties.InternalServiceProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ecosystemMetrics = ecosystemMetrics;
        this.serviceSecrets = properties.asSecretMap();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String serviceId = request.getHeader("X-Service-Id");
        String tsHeader = request.getHeader("X-Service-Timestamp");
        String nonce = request.getHeader("X-Service-Nonce");
        String signature = request.getHeader("X-Service-Signature");

        if (!StringUtils.hasText(serviceId) || !StringUtils.hasText(tsHeader)
                || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            ecosystemMetrics.recordInternalApiCall(serviceId, "missing_headers");
            reject(response, HttpStatus.UNAUTHORIZED, "MISSING_SERVICE_HEADERS", "缺少服务调用头");
            return;
        }
        if (!serviceSecrets.containsKey(serviceId)) {
            log.warn("[internal] 未知 service id: {}", serviceId);
            ecosystemMetrics.recordInternalApiCall(serviceId, "unknown_service");
            reject(response, HttpStatus.UNAUTHORIZED, "UNKNOWN_SERVICE", "未授权的调用方");
            return;
        }
        if (!NONCE_PATTERN.matcher(nonce).matches()) {
            ecosystemMetrics.recordInternalApiCall(serviceId, "invalid_nonce");
            reject(response, HttpStatus.BAD_REQUEST, "INVALID_NONCE", "nonce 不合法");
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(tsHeader);
        } catch (NumberFormatException e) {
            ecosystemMetrics.recordInternalApiCall(serviceId, "invalid_timestamp");
            reject(response, HttpStatus.BAD_REQUEST, "INVALID_TIMESTAMP", "timestamp 不合法");
            return;
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > TIMESTAMP_WINDOW_MS) {
            log.warn("[internal] 时间戳过期: ts={}, now={}, diff={}", timestamp, now, now - timestamp);
            ecosystemMetrics.recordInternalApiCall(serviceId, "timestamp_skew");
            reject(response, HttpStatus.UNAUTHORIZED, "TIMESTAMP_SKEW", "请求时间戳过期");
            return;
        }

        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
        String body = cached.cachedBodyAsString();

        String expectedSig;
        try {
            expectedSig = computeSignature(
                    serviceSecrets.get(serviceId),
                    request.getMethod(),
                    request.getRequestURI(),
                    tsHeader,
                    nonce,
                    body
            );
        } catch (Exception e) {
            log.error("[internal] 计算签名失败", e);
            ecosystemMetrics.recordInternalApiCall(serviceId, "sign_fail");
            reject(response, HttpStatus.INTERNAL_SERVER_ERROR, "SIGN_FAIL", "签名计算异常");
            return;
        }

        if (!constantTimeEquals(expectedSig, signature.toLowerCase(Locale.ROOT))) {
            log.warn("[internal] 签名不匹配: serviceId={}, uri={}", serviceId, request.getRequestURI());
            ecosystemMetrics.recordInternalApiCall(serviceId, "bad_signature");
            reject(response, HttpStatus.UNAUTHORIZED, "BAD_SIGNATURE", "签名校验失败");
            return;
        }

        // 只有通过验签的请求才占用 nonce。否则攻击者可以抢先提交伪造签名，
        // 把合法请求的 nonce 写入 Redis，造成可利用的 nonce-poisoning 拒绝服务。
        String nonceKey = "auth:internal:nonce:" + serviceId + ":" + nonce;
        Boolean firstSeen = redisTemplate.opsForValue().setIfAbsent(
                nonceKey, "1", TIMESTAMP_WINDOW_MS * 2, TimeUnit.MILLISECONDS);
        if (!Boolean.TRUE.equals(firstSeen)) {
            log.warn("[internal] nonce 复用: serviceId={}", serviceId);
            ecosystemMetrics.recordInternalApiCall(serviceId, "duplicate_nonce");
            reject(response, HttpStatus.CONFLICT, "DUPLICATE_NONCE", "请求重复");
            return;
        }

        ecosystemMetrics.recordInternalApiCall(serviceId, "ok");
        request.setAttribute("internal.serviceId", serviceId);
        chain.doFilter(cached, response);
    }

    private String computeSignature(String secret, String method, String path, String timestamp, String nonce, String body)
            throws NoSuchAlgorithmException, UnsupportedEncodingException,
            java.security.InvalidKeyException {
        String bodyHash = sha256Hex(body == null ? "" : body);
        String payload = String.join("\n",
                method.toUpperCase(Locale.ROOT),
                path,
                timestamp,
                nonce,
                bodyHash
        );
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return toHex(sig);
    }

    private static String sha256Hex(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return toHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private void reject(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.failed(code + ": " + message)));
    }
}
