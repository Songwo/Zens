package com.campus.trend.campus_pulse.common.notification;

import lombok.Getter;

/**
 * 通知类型枚举 —— 代替散落在各处的字符串字面量。
 * code 字段持久化到 `notifications.type`；前端可基于 category 做视觉区分。
 */
@Getter
public enum NotificationType {

    // --- 社交类：内容互动 ---
    LIKE("like", NotificationCategory.SOCIAL),
    FAVORITE("favorite", NotificationCategory.SOCIAL),
    REPLY("reply", NotificationCategory.SOCIAL),
    FOLLOW("follow", NotificationCategory.SOCIAL),
    MENTION("mention", NotificationCategory.SOCIAL),

    // --- 安全类：账户安全事件 ---
    SECURITY_ALERT("security_alert", NotificationCategory.SECURITY),
    NEW_DEVICE_LOGIN("new_device_login", NotificationCategory.SECURITY),
    SESSION_TERMINATED("session_terminated", NotificationCategory.SECURITY),
    PASSWORD_CHANGED("password_changed", NotificationCategory.SECURITY),
    PASSWORD_RESET("password_reset", NotificationCategory.SECURITY),
    TWO_FACTOR_ENABLED("two_factor_enabled", NotificationCategory.SECURITY),
    TWO_FACTOR_DISABLED("two_factor_disabled", NotificationCategory.SECURITY),
    LOGIN_FAILED_BURST("login_failed_burst", NotificationCategory.SECURITY),

    // --- 系统公告 ---
    SYSTEM("system", NotificationCategory.SYSTEM);

    private final String code;
    private final NotificationCategory category;

    NotificationType(String code, NotificationCategory category) {
        this.code = code;
        this.category = category;
    }

    /** 根据持久化的 code 反查 category，未知 code 归 SYSTEM。 */
    public static NotificationCategory categoryOf(String code) {
        if (code == null || code.isBlank()) {
            return NotificationCategory.SYSTEM;
        }
        for (NotificationType t : values()) {
            if (t.code.equals(code)) {
                return t.category;
            }
        }
        return NotificationCategory.SYSTEM;
    }

    public enum NotificationCategory {
        SOCIAL,
        SECURITY,
        SYSTEM
    }
}
