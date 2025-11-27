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
import java.util.Map;
import java.util.Objects;
/**
 * 功能：Jwt工具包，包含生成和解析以及解析的附加方法
 * 作者：Song
 * 日期：2025/11/26
 */
@Slf4j
@Component
public class JwtUtil {

    //Jwt密钥
    @Value("${jwt.SECRET_KEY}")
    private static final String secretKey = "RwE7qC8ce3rE5eVTPVm1ZJNmD5JyAuDN1Xupe16p6gueoN9Ye5BYMReMm8ZMcv0u";
    //Jwt过期时间
    @Value("${jwt.ExpireTime}")
    private static final int ExpireTime = 3600000;

    //获取签名
    public static Key getJwtKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 根据用户自定义Claims生成Token
    */
    public static String GenerateToken(String userID,Map<String, Object> claims) {
        return Jwts.builder()
                .setSubject(userID)
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ExpireTime))
                .signWith(getJwtKey())
                .compact();

    }

    /**
     * 解析Token
    */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getJwtKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        }catch (JwtException e){
            log.error("JWT解析失败：{}",e.getMessage());
            return null;
        }
    }

    //获取Claim对象，根据不同名字获取值，通用内置方法
    public static <T> T getClaimByToken(String token,String name, Class<T> clazz) {
        Claims claims = parseToken(token);
        if (Objects.isNull(claims))
            return null;
        return claims.get(name, clazz);
    }

    //获取用户ID
    public static String getUserID(String token) {
        return Objects.requireNonNull(parseToken(token)).getSubject();
    }

    //获取用户名
    public static String getUsername(String token) {
        return getClaimByToken(token,"username", String.class);
    }

    //获取用户角色
    public static String getRole(String token) {
        return getClaimByToken(token,"role", String.class);
    }

    //获取用户头像
    public static String getAvatar(String token) {
        return getClaimByToken(token,"avatar", String.class);
    }

    //检查Token是否过期
    public static boolean isExpired(String token) {
        Claims claims = parseToken(token);
        if (Objects.isNull(claims))
            return false;
        return claims.getExpiration().before(new Date());
    }

    //检查Token是否有效
    public boolean validateToken(String token, UserDetails userDetails) {
        String username = getUsername(token);
        return username.equals(userDetails.getUsername()) && !isExpired(token);
    }

    //获取请求头中的Token
    public String getToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

}
