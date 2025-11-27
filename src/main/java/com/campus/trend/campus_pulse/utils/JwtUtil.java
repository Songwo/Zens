package com.campus.trend.campus_pulse.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import java.util.Map;
import java.util.Objects;

/**
 * 功能：Jwt工具包，包含生成和解析以及解析的附加方法
 * 作者：Song
 * 日期：2025/11/26
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;   // 注入成功

    @Value("${jwt.access-expire}")
    private long accessExpire;  // 毫秒

    @Value("${jwt.refresh-expire}")
    private long refreshExpire; // 毫秒

    private Key getKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /** 生成通用 Claims */
    public Map<String, Object> buildClaims(String username, int role, String avatar) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("role", role);
        claims.put("avatar", avatar);
        return claims;
    }

    /** 生成 AccessToken */
    public String generateAccessToken(String userId, Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpire))
                .signWith(getKey())
                .compact();
    }

    /** 生成 RefreshToken */
    public String generateRefreshToken(String userId, Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpire))
                .signWith(getKey())
                .compact();
    }

    /** 解析 Token */
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
        return c.getSubject();
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
        return username.equals(userDetails.getUsername()) && !isExpired(token);
    }

    /** 获取请求头中的 Token */
    public String getToken(HttpServletRequest request) {
        String a = request.getHeader("Authorization");
        if (a != null && a.startsWith("Bearer ")) {
            return a.substring(7);
        }
        return null;
    }
}

