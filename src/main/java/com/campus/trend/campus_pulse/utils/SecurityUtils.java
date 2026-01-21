package com.campus.trend.campus_pulse.utils;

import com.campus.trend.campus_pulse.security.AuthUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类
 */
public class SecurityUtils {

    /**
     * 获取当前登录用户ID
     * @return 用户ID，未登录返回 null
     */
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
            // 静默处理，返回 null 表示匿名
        }
        return null;
    }

    /**
     * 获取当前登录用户信息
     * @return AuthUser，未登录返回 null
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
            // 静默处理
        }
        return null;
    }

    /**
     * 获取当前认证用户，如果未认证则抛出异常
     * @return AuthUser
     * @throws IllegalStateException 如果用户未认证
     */
    public static AuthUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 检查是否已认证
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            throw new IllegalStateException("用户未认证或已过期，无法访问用户信息。");
        }

        Object principal = authentication.getPrincipal();

        // 检查 Principal 是否是 AuthUser 类型
        if (principal instanceof AuthUser) {
            return (AuthUser) principal;
        }

        throw new IllegalStateException("认证主体类型错误: " + principal.getClass().getName());
    }
}
