package com.campus.trend.campus_pulse.config;

public class SecurityWhitelist {

        public static final String[] AUTH_WHITELIST = {
                        // Song：用户认证相关接口
                        "/auth/login",
                        "/auth/register",
                        "/auth/send-code",
                        "/auth/verify-code",
                        "/auth/check-email",
                        "/auth/check-username",
                        "/auth/login-fail-count/**",
                        "/auth/reset-password",
                        "/auth/refresh",
                        "/auth/github/authorize-url",
                        "/auth/github/login",
                        "/auth/2fa/verify-login",
                        "/static/**",
                        "/uploads/**",
                        "/ws/**",
        };

        public static final String[] PUBLIC_GET_URLS = {
                        "/post/**",
                        "/category/**",
                        "/categories/**",
                        "/tag/hot",
                        "/tag/hot/**",
                        "/tag/search",
                        "/recommend/**",
                        "/trend-stat/**",
                        "/heat-rank/**",
                        "/public/**",
                        "/comment/post/**",
                        "/stats/**",
                        "/section/**",
                        "/user/public/**",
                        "/user/search",
                        "/invite/validate",
                        "/invite/required",
                        "/follow/stats/**",
                        "/level/thresholds",
                        "/changelog/list",
                        "/sso/clients/public/**",
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus",
        };

        public static final String[] PUBLIC_POST_URLS = {
                        "/post/search-lists",
                        "/comment/create",
        };
}
