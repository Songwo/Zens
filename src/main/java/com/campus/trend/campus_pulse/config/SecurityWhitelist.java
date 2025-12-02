package com.campus.trend.campus_pulse.config;

public class SecurityWhitelist {

    /**
     * 不需要登录即可访问的 URL
     */
    public static final String[] AUTH_WHITELIST = {
            //用户认证相关接口
            "/auth/login",
            "/auth/register",
            "/static/**",

            //用户业务相关接口
//            "/sys-user/test",
//            "/sys-user/all",
//            "/sys-user/profile",
//            "/sys-user/simple-profile",
    };
}
