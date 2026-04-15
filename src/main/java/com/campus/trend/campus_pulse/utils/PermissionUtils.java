package com.campus.trend.campus_pulse.utils;

import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class PermissionUtils {

    private static final String DEFAULT_ROLE = "ROLE_USER";
    private static UserMapper userMapper;

    private static final Map<String, Integer> ROLE_LEVEL_MAP = Map.of(
            "ROLE_USER", 1,
            "ROLE_MODERATOR", 2,
            "ROLE_ADMIN", 3,
            "ROLE_SUPER_ADMIN", 4
    );

    public PermissionUtils(UserMapper userMapper) {
        PermissionUtils.userMapper = userMapper;
    }

    public static int getRoleLevel(String role) {
        if (role == null) return 0;
        return ROLE_LEVEL_MAP.getOrDefault(role, 0);
    }

    public static String getCurrentUserRole() {
        String currentUserId = SecurityUtils.getCurrentUserId();
        String roleFromAuth = resolveRoleFromAuthentication(currentUserId);
        return roleFromAuth != null ? roleFromAuth : DEFAULT_ROLE;
    }

    public static boolean isSuperAdmin() {
        return "ROLE_SUPER_ADMIN".equals(getCurrentUserRole());
    }

    public static boolean canManageRole(String operatorRole, String targetRole) {
        return getRoleLevel(operatorRole) > getRoleLevel(targetRole);
    }

    public static String getUserRole(String userId) {
        if (!StringUtils.hasText(userId)) return DEFAULT_ROLE;
        String roleFromAuth = resolveRoleFromAuthentication(userId);
        if (roleFromAuth != null) {
            return roleFromAuth;
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getRole() == null) return DEFAULT_ROLE;
        return user.getRole();
    }

    public static boolean isAdmin() {
        String role = getCurrentUserRole();
        return "ROLE_ADMIN".equals(role) || "ROLE_SUPER_ADMIN".equals(role);
    }

    public static boolean isAdminOrModerator() {
        String role = getCurrentUserRole();
        return "ROLE_ADMIN".equals(role)
                || "ROLE_SUPER_ADMIN".equals(role)
                || "ROLE_MODERATOR".equals(role);
    }

    public static boolean isUserAdmin(String userId) {
        String role = getUserRole(userId);
        return "ROLE_ADMIN".equals(role) || "ROLE_SUPER_ADMIN".equals(role);
    }

    public static boolean isUserAdminOrModerator(String userId) {
        String role = getUserRole(userId);
        return role.equals("ROLE_ADMIN") || role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_MODERATOR");
    }

    public static boolean hasPermission(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(p -> p.equals(permission));
    }

    private static String resolveRoleFromAuthentication(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        String currentUserId = SecurityUtils.getCurrentUserId();
        if (!userId.equals(currentUserId)) {
            return null;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        // 当前登录用户的角色本来就在鉴权上下文里，先从这里拿，别每次都回表查 user。
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(ROLE_LEVEL_MAP::containsKey)
                .max((a, b) -> getRoleLevel(a) - getRoleLevel(b))
                .orElse(DEFAULT_ROLE);
    }
}
