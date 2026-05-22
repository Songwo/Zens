package com.campus.trend.campus_pulse.utils;

import com.campus.trend.campus_pulse.security.AuthUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Song：安全工具类
 */
public class SecurityUtils {

    public static String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof AuthUser) {
                    return ((AuthUser) principal).getUser().getId();
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * Song：获取当前登录用户信息
     */
    public static AuthUser getLoginUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof AuthUser) {
                    return (AuthUser) principal;
                }
            }
        } catch (Exception e) {
            // Song：静默处理
        }
        return null;
    }

    /**
     * Song：获取当前认证用户，如果未认证则抛出异常
     */
    public static AuthUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Song：检查是否已认证
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            throw new IllegalStateException("用户未认证或已过期，无法访问用户信息。");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof AuthUser) {
            return (AuthUser) principal;
        }

        throw new IllegalStateException("认证主体类型错误: " + principal.getClass().getName());
    }
}
