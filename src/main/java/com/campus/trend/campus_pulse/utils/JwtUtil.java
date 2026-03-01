package com.campus.trend.campus_pulse.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
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

    private Key getKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
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
            return Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.info("JWT 解析失败: {}", e.getMessage());
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
