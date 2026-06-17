package com.campus.trend.campus_pulse.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * IP黑名单过滤器（防刷）
 * 自动识别高频IP，临时加入黑名单
 *
 * 启用配置：
 * campus.security.ip-blacklist.enabled=true
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "campus.security.ip-blacklist", name = "enabled", havingValue = "true")
public class IpBlacklistFilter implements Filter {

    private final StringRedisTemplate redisTemplate;

    private static final String IP_REQUEST_COUNT_PREFIX = "ip:req:";
    private static final String IP_BLACKLIST_PREFIX = "ip:blacklist:";

    // 1分钟内超过200次请求，加入黑名单
    private static final int MAX_REQUESTS_PER_MINUTE = 200;
    private static final Duration COUNT_WINDOW = Duration.ofMinutes(1);
    private static final Duration BLACKLIST_DURATION = Duration.ofMinutes(30);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIp(httpRequest);
        String blacklistKey = IP_BLACKLIST_PREFIX + clientIp;

        // 1. 检查是否在黑名单
        Boolean isBlacklisted = redisTemplate.hasKey(blacklistKey);
        if (Boolean.TRUE.equals(isBlacklisted)) {
            log.warn("🚫 IP黑名单拦截: {}", clientIp);
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"code\":4003,\"message\":\"访问过于频繁，已被临时封禁\"}");
            return;
        }

        // 2. 统计请求次数
        String countKey = IP_REQUEST_COUNT_PREFIX + clientIp;
        Long count = redisTemplate.opsForValue().increment(countKey);

        if (count == null) {
            count = 0L;
        }

        // 首次请求，设置过期时间
        if (count == 1) {
            redisTemplate.expire(countKey, COUNT_WINDOW);
        }

        // 3. 超过阈值，加入黑名单
        if (count > MAX_REQUESTS_PER_MINUTE) {
            redisTemplate.opsForValue().set(blacklistKey, "1", BLACKLIST_DURATION);
            log.warn("⚠️ IP加入黑名单: {} ({}次请求/分钟)", clientIp, count);

            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"code\":4003,\"message\":\"请求过于频繁，已被临时封禁30分钟\"}");
            return;
        }

        // 4. 放行
        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 取第一个IP（如果有多个）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }
}
