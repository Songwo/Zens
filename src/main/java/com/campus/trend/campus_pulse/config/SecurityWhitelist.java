package com.campus.trend.campus_pulse.config;

import java.util.LinkedHashSet;
import java.util.Set;

public class SecurityWhitelist {

        private static String[] withApiAliases(String... paths) {
                        Set<String> results = new LinkedHashSet<>();
                        for (String path : paths) {
                                        if (path == null || path.isBlank()) {
                                                        continue;
                                        }
                                        results.add(path);
                                        if (path.startsWith("/api/")
                                                        || path.startsWith("/actuator/")
                                                        || "/".equals(path)) {
                                                        continue;
                                        }
                                        results.add("/api" + path);
                        }
                        return results.toArray(new String[0]);
        }

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
                        // Song：前端静态入口与 PWA 资源。同源部署时这些文件会经过 Spring Security，
                        // 必须放行，否则浏览器会出现 manifest/sw 403。
                        "/",
                        "/index.html",
                        "/favicon.ico",
                        "/manifest.webmanifest",
                        "/manifest.json",
                        "/registerSW.js",
                        "/sw.js",
                        "/workbox-*.js",
                        "/robots.txt",
                        "/sitemap.xml",
                        "/feed.xml",
                        "/rss.xml",
                        "/logo.png",
                        "/logo-horizontal.png",
                        "/icons/**",
                        "/assets/**",
                        "/static/**",
                        "/uploads/**",
                        "/ws/**",
        };

        public static final String[] PUBLIC_GET_URLS = withApiAliases(
                        "/post/*",
                        "/category/**",
                        "/categories/**",
                        "/agent/health",
                        "/tag/hot",
                        "/tag/hot/**",
                        "/tag/search",
                        "/recommend/**",
                        "/trend-stat/**",
                        "/heat-rank/**",
                        "/public/**",
                        "/seo/**",
                        "/comment/post/**",
                        "/stats/**",
                        "/section/**",
                        "/user/public/**",
                        "/user/search",
                        "/invite/validate",
                        "/invite/required",
                        "/follow/stats/**",
                        "/level/thresholds",
                        "/trust-level/thresholds",
                        "/search/hot-keywords",
                        "/search/suggestions",
                        "/poll/by-post/**",
                        "/changelog/list",
                        "/short-link/**",
                        "/sso/clients/public/**",
                        "/onebox/preview",
                        "/reaction",
                        "/performance/web-vitals/summary",
                        "/performance/web-vitals/events",
                        "/supporter/plans",
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus");

        public static final String[] PUBLIC_POST_URLS = withApiAliases(
                        "/post/search-lists",
                        "/documents/list",
                        "/reaction/batch",
                        "/view-log/record",
                        "/view-log/heartbeat",
                        "/agent/community-qa/ask",
                        "/agent/community-qa/ask-stream",
                        "/agent/community-qa/search",
                        "/performance/web-vitals",
                        "/payment/callback/**",
                        "/growth/events",
                        "/comment/create",
                        "/short-link/comment");
}
