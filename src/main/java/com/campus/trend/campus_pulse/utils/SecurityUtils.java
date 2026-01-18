package com.campus.trend.campus_pulse.utils;

import com.campus.trend.campus_pulse.security.AuthSysUser;
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
                if (principal instanceof AuthSysUser) {
                    return ((AuthSysUser) principal).getSysUser().getId();
                }
            }
        } catch (Exception e) {
            // 静默处理，返回 null 表示匿名
        }
        return null;
    }

    /**
     * 获取当前登录用户信息
     * @return AuthSysUser，未登录返回 null
     */
    public static AuthSysUser getLoginUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof AuthSysUser) {
                    return (AuthSysUser) principal;
                }
            }
        } catch (Exception e) {
            // 静默处理
        }
        return null;
    }
}
