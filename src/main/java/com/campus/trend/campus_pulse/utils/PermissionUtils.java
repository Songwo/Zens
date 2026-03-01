package com.campus.trend.campus_pulse.utils;

import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PermissionUtils {

    private static UserMapper userMapper;

    public PermissionUtils(UserMapper userMapper) {
        PermissionUtils.userMapper = userMapper;
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> r.equals("ROLE_ADMIN") || r.equals("ROLE_SUPER_ADMIN"));
    }

    public static boolean isAdminOrModerator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> r.equals("ROLE_ADMIN") || r.equals("ROLE_SUPER_ADMIN") || r.equals("ROLE_MODERATOR"));
    }

    public static boolean isUserAdmin(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getRole() == null) return false;
        return user.getRole().equals("ROLE_ADMIN") || user.getRole().equals("ROLE_SUPER_ADMIN");
    }

    public static boolean isUserAdminOrModerator(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getRole() == null) return false;
        String role = user.getRole();
        return role.equals("ROLE_ADMIN") || role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_MODERATOR");
    }

    public static boolean hasPermission(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(p -> p.equals(permission));
    }
}
