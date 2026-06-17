package com.campus.trend.campus_pulse.aspect;

import com.campus.trend.campus_pulse.annotation.RateLimit;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 接口限流切面
 * 使用Redisson的RateLimiter实现分布式限流
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedissonClient redissonClient;

    @Around("@annotation(com.campus.trend.campus_pulse.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        if (rateLimit == null) {
            return pjp.proceed();
        }

        // 构建限流key
        String limitKey = buildLimitKey(rateLimit, method);

        // 获取或创建限流器
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(limitKey);

        // 初始化限流器配置（如果未初始化）
        if (!rateLimiter.isExists()) {
            // 设置速率：windowSeconds内最多limit次请求
            rateLimiter.trySetRate(
                RateType.OVERALL,
                rateLimit.limit(),
                rateLimit.windowSeconds(),
                RateIntervalUnit.SECONDS
            );
        }

        // 尝试获取令牌
        boolean acquired = rateLimiter.tryAcquire(1);

        if (!acquired) {
            log.warn("⚠️ 限流拦截: key={}, limit={}/{}",
                limitKey, rateLimit.limit(), rateLimit.windowSeconds());

            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                String.format("请求过于频繁，请%d秒后再试", rateLimit.windowSeconds()));
        }

        // 放行
        return pjp.proceed();
    }

    /**
     * 构建限流key
     */
    private String buildLimitKey(RateLimit rateLimit, Method method) {
        String prefix = "rate_limit:" + rateLimit.key() + ":" +
                       method.getDeclaringClass().getSimpleName() + "." +
                       method.getName();

        switch (rateLimit.limitType()) {
            case USER:
                // 按用户限流
                try {
                    String userId = SecurityUtils.getCurrentUserId();
                    return prefix + ":user:" + userId;
                } catch (Exception e) {
                    // 未登录用户按IP限流
                    return prefix + ":ip:" + getClientIp();
                }

            case IP:
                // 按IP限流
                return prefix + ":ip:" + getClientIp();

            case GLOBAL:
                // 全局限流
                return prefix + ":global";

            default:
                return prefix;
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                return "unknown";
            }

            HttpServletRequest request = attributes.getRequest();
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

        } catch (Exception e) {
            log.warn("获取客户端IP失败: {}", e.getMessage());
            return "unknown";
        }
    }
}
