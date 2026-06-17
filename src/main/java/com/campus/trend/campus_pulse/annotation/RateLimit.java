package com.campus.trend.campus_pulse.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 * 使用Redis实现滑动窗口限流
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流key前缀
     */
    String key() default "rate_limit";

    /**
     * 限流时间窗口（秒）
     */
    int windowSeconds() default 60;

    /**
     * 时间窗口内最大请求次数
     */
    int limit() default 100;

    /**
     * 限流类型
     */
    LimitType limitType() default LimitType.USER;

    /**
     * 限流类型枚举
     */
    enum LimitType {
        /** 按用户限流 */
        USER,
        /** 按IP限流 */
        IP,
        /** 全局限流 */
        GLOBAL
    }
}
