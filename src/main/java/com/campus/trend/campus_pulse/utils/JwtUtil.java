package com.campus.trend.campus_pulse.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Song：说明
 * Song：说明
 * Song：日期：2025/11/26
 */
@Component
@Slf4j
public class JwtUtil {

    // 这个工具在过滤器里走得很频繁，签名 key 和 parser 没必要每次都现算一遍。
    private volatile Key signingKey;
    private volatile JwtParser jwtParser;

    @Value("${jwt.secret}")
    private String secretKey; // Song：注入成功

    @Value("${jwt.access-expire}")
    private long accessExpire; // Song：毫秒

    @Value("${jwt.refresh-expire}")
    private long refreshExpire; // Song：毫秒

    @Value("${jwt.remember-access-multiplier:7}")
    private long rememberAccessMultiplier;

    @Value("${jwt.remember-refresh-multiplier:4}")
    private long rememberRefreshMultiplier;

    @Value("${jwt.sliding-renew-threshold:900000}")
    private long slidingRenewThreshold;

    private Key getKey() {
        Key cached = signingKey;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (signingKey == null) {
                signingKey = buildSigningKey();
            }
            return signingKey;
        }
    }

    private Key buildSigningKey() {
        byte[] keyBytes = (secretKey == null ? "" : secretKey).getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            log.warn("JWT 密钥长度过短({}字节)，已自动使用 SHA-256 派生密钥。请尽快将 JWT_SECRET 配置为至少 32 字节随机串。", keyBytes.length);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                keyBytes = digest.digest(keyBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("JWT 密钥派生失败: SHA-256 不可用", e);
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private JwtParser getParser() {
        JwtParser cached = jwtParser;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (jwtParser == null) {
                jwtParser = Jwts.parserBuilder()
                        .setSigningKey(getKey())
                        .build();
            }
            return jwtParser;
        }
    }

    /* Song：说明 */
    public Map<String, Object> buildClaims(String username, List<String> roles, String avatar) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("roles", roles);
        claims.put("avatar", avatar);
        return claims;
    }

    /* Song：说明 */
    public String generateAccessToken(String userId, Map<String, Object> claims, boolean rememberMe) {
        long expire = resolveAccessExpireMs(rememberMe);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expire))
                .signWith(getKey())
                .compact();
    }

    /* Song：兼容旧方法 */
    public String generateAccessToken(String userId, Map<String, Object> claims) {
        return generateAccessToken(userId, claims, false);
    }

    /* Song：说明 */
    public String generateRefreshToken(String userId, Map<String, Object> claims, boolean rememberMe) {
        long expire = resolveRefreshExpireMs(rememberMe);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expire))
                .signWith(getKey())
                .compact();
    }

    /* Song：兼容旧方法 */
    public String generateRefreshToken(String userId, Map<String, Object> claims) {
        return generateRefreshToken(userId, claims, false);
    }

    /* Song：说明 */
    public Claims parse(String token) {
        try {
            return getParser()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            // 非法 token 在日常请求里很常见，打到 debug 就够了，别把业务日志刷掉。
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public String getUserId(String token) {
        Claims c = parse(token);
        return c == null ? null : c.getSubject();
    }

    public String getUsername(String token) {
        Claims c = parse(token);
        return c == null ? null : c.get("username", String.class);
    }

    public boolean isExpired(String token) {
        Claims c = parse(token);
        return c == null || c.getExpiration().before(new Date());
    }

    public boolean validate(String token) {
        return !isExpired(token);
    }

    public boolean validate(String token, UserDetails userDetails) {
        String username = getUsername(token);
        return username != null
                && userDetails != null
                && username.equals(userDetails.getUsername())
                && !isExpired(token);
    }

    public long getRemainingMillis(String token) {
        Claims claims = parse(token);
        if (claims == null || claims.getExpiration() == null) {
            return -1L;
        }
        return claims.getExpiration().getTime() - System.currentTimeMillis();
    }

    public boolean shouldRenewAccessToken(String token) {
        long remainingMillis = getRemainingMillis(token);
        return remainingMillis > 0 && remainingMillis <= Math.max(60000L, slidingRenewThreshold);
    }

    public long resolveAccessExpireMs(boolean rememberMe) {
        return rememberMe ? accessExpire * Math.max(1, rememberAccessMultiplier) : accessExpire;
    }

    public long resolveRefreshExpireMs(boolean rememberMe) {
        return rememberMe ? refreshExpire * Math.max(1, rememberRefreshMultiplier) : refreshExpire;
    }

    public String getClaimString(String token, String key) {
        Claims c = parse(token);
        return c == null ? null : c.get(key, String.class);
    }

    public Boolean getClaimBoolean(String token, String key) {
        Claims c = parse(token);
        return c == null ? null : c.get(key, Boolean.class);
    }

    /* Song：说明 */
    public String getToken(HttpServletRequest request) {
        String a = request.getHeader("Authorization");
        if (a != null && a.startsWith("Bearer ")) {
            return a.substring(7);
        }
        return null;
    }
}
