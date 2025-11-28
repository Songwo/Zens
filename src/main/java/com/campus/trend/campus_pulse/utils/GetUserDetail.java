package com.campus.trend.campus_pulse.utils;

import com.campus.trend.campus_pulse.security.AuthSysUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class GetUserDetail {

    public static AuthSysUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 检查是否已认证
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            throw new IllegalStateException("用户未认证或已过期，无法访问用户信息。");
        }

        Object principal = authentication.getPrincipal();

        // 检查 Principal 是否是 AuthSysUser 类型
        if (principal instanceof AuthSysUser) {
            return (AuthSysUser) principal;
        }

        throw new IllegalStateException("认证主体类型错误: " + principal.getClass().getName());
    }

}
