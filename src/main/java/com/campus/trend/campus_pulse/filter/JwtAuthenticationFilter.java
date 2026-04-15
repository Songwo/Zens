package com.campus.trend.campus_pulse.filter;

import com.campus.trend.campus_pulse.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    public JwtAuthenticationFilter(UserDetailsService userDetailsService,
            JwtUtil jwtUtil,
            StringRedisTemplate stringRedisTemplate) {
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws IOException, ServletException {

        String accessToken = jwtUtil.getToken(request);

        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = jwtUtil.getUserId(accessToken);
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtUtil.validate(accessToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        String tokenType = jwtUtil.getClaimString(accessToken, "typ");
        if (StringUtils.hasText(tokenType) && !"access".equalsIgnoreCase(tokenType)) {
            filterChain.doFilter(request, response);
            return;
        }

        String sessionId = jwtUtil.getClaimString(accessToken, "sid");
        String tokenDeviceId = jwtUtil.getClaimString(accessToken, "did");
        String requestDeviceId = request.getHeader("X-Device-Id");
        if (StringUtils.hasText(tokenDeviceId)
                && StringUtils.hasText(requestDeviceId)
                && !tokenDeviceId.equals(requestDeviceId)) {
            log.warn("设备ID校验失败: userId={}, sid={}, tokenDid={}, reqDid={}",
                    userId, sessionId, tokenDeviceId, requestDeviceId);
            filterChain.doFilter(request, response);
            return;
        }

        if (StringUtils.hasText(sessionId)) {
            String redisDeviceId = stringRedisTemplate.opsForValue().get("auth:device:" + userId + ":" + sessionId);
            if (StringUtils.hasText(redisDeviceId) && !StringUtils.hasText(requestDeviceId)) {
                log.warn("设备头缺失: userId={}, sid={}", userId, sessionId);
                filterChain.doFilter(request, response);
                return;
            }
            if (StringUtils.hasText(redisDeviceId)
                    && StringUtils.hasText(requestDeviceId)
                    && !redisDeviceId.equals(requestDeviceId)) {
                log.warn("会话设备校验失败: userId={}, sid={}, redisDid={}, reqDid={}",
                        userId, sessionId, redisDeviceId, requestDeviceId);
                filterChain.doFilter(request, response);
                return;
            }
            if (StringUtils.hasText(redisDeviceId)
                    && StringUtils.hasText(tokenDeviceId)
                    && !redisDeviceId.equals(tokenDeviceId)) {
                log.warn("令牌设备与会话不一致: userId={}, sid={}, tokenDid={}, redisDid={}",
                        userId, sessionId, tokenDeviceId, redisDeviceId);
                filterChain.doFilter(request, response);
                return;
            }
        }

        String redisAccess;
        if (StringUtils.hasText(sessionId)) {
            redisAccess = stringRedisTemplate.opsForValue().get("auth:access:" + userId + ":" + sessionId);
            if (!StringUtils.hasText(redisAccess)) {
                // 兼容旧key
                redisAccess = stringRedisTemplate.opsForValue().get("access_token" + userId);
            }
        } else {
            redisAccess = stringRedisTemplate.opsForValue().get("access_token" + userId);
        }

        if (!accessToken.equals(redisAccess)) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = jwtUtil.getUsername(accessToken);
        if (username != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails ud = userDetailsService.loadUserByUsername(username);

            if (jwtUtil.validate(accessToken, ud)) {

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        ud,
                        null,
                        ud.getAuthorities());

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("JWT认证通过: userId={}", userId);
                maybeRenewAccessToken(response, accessToken, userId, sessionId);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void maybeRenewAccessToken(HttpServletResponse response,
                                       String accessToken,
                                       String userId,
                                       String sessionId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId) || !jwtUtil.shouldRenewAccessToken(accessToken)) {
            return;
        }

        String refreshKey = "auth:refresh:" + userId + ":" + sessionId;
        String refreshState = stringRedisTemplate.opsForValue().get(refreshKey);
        if (!StringUtils.hasText(refreshState)) {
            return;
        }

        Claims claims = jwtUtil.parse(accessToken);
        if (claims == null) {
            return;
        }

        boolean rememberMe = Boolean.TRUE.equals(claims.get("remember", Boolean.class));
        Map<String, Object> renewedClaims = new HashMap<>(jwtUtil.buildClaims(
                claims.get("username", String.class),
                claims.get("roles", List.class),
                claims.get("avatar", String.class)));
        renewedClaims.put("sid", claims.get("sid", String.class));
        renewedClaims.put("did", claims.get("did", String.class));
        renewedClaims.put("remember", rememberMe);
        renewedClaims.put("typ", "access");

        String renewedAccessToken = jwtUtil.generateAccessToken(userId, renewedClaims, rememberMe);
        long accessTtlMs = jwtUtil.resolveAccessExpireMs(rememberMe);
        stringRedisTemplate.opsForValue().set("auth:access:" + userId + ":" + sessionId,
                renewedAccessToken,
                accessTtlMs,
                TimeUnit.MILLISECONDS);
        stringRedisTemplate.opsForValue().set("access_token" + userId,
                renewedAccessToken,
                accessTtlMs,
                TimeUnit.MILLISECONDS);

        response.setHeader("Authorization", "Bearer " + renewedAccessToken);
        response.setHeader("X-Access-Token", renewedAccessToken);
        response.setHeader("X-Access-Token-Expires-In", String.valueOf(accessTtlMs));
    }
}
