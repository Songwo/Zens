package com.campus.trend.campus_pulse.config;

public class SecurityWhitelist {

        /**
         * 不需要登录即可访问的 URL
         */
        /**
         * 不需要登录即可访问的 URL (所有 Method)
         */
        public static final String[] AUTH_WHITELIST = {
                        // 用户认证相关接口
                        "/auth/login",
                        "/auth/register",
                        "/auth/captcha",
                        "/auth/send-code",
                        "/auth/send-login-code",
                        "/auth/login-fail-count/**",
                        "/static/**",
                        "/uploads/**",
        };

        /**
         * 仅允许 GET 请求的公开接口
         */
        public static final String[] PUBLIC_GET_URLS = {
                        "/post/**", // 查看帖子详情 (注意 SecurityConfig 需配置仅限 GET)
                        "/category/all", // 查看分类
                        "/category/**",
                        "/category/tree",
                        "/tag/hot", // 热门标签
                        "/recommend/**", // 推荐系统 (标签推荐、帖子推荐、相似推荐)
                        "/trend-stat/**", // 趋势统计 (关键词云、分类饼图、热度排行)
                        "/heat-rank/**", // 实时热度排行
                        "/comment/post/**", // 查看帖子评论
                        "/category/list/**",
                        "/tag/hot/**"
        };

        /**
         * 仅允许 POST 请求的公开接口 (通常用于复杂查询)
         */
        public static final String[] PUBLIC_POST_URLS = {
                        "/post/search-lists", // 帖子列表查询
                        "/post/extract-tags", // 提取标签(工具类)
                        "/comment/create", // 匿名评论发布（未登录用户也可以发布匿名评论）
        };
}
