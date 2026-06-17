package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.entity.User;

import java.util.List;
import java.util.Map;

/**
 * 批量用户查询服务
 * 用于解决列表页N+1查询问题
 */
public interface BatchUserService {

    /**
     * 批量获取用户信息（带缓存）
     * @param userIds 用户ID列表
     * @return userId -> User映射
     */
    Map<String, User> batchGetUsers(List<String> userIds);

    /**
     * 批量获取用户基本信息（精简版，只包含常用字段）
     * @param userIds 用户ID列表
     * @return userId -> SimpleUser映射
     */
    Map<String, SimpleUserInfo> batchGetSimpleUsers(List<String> userIds);

    /**
     * 精简用户信息（列表渲染用）
     */
    class SimpleUserInfo {
        private String id;
        private String username;
        private String nickname;
        private String avatar;
        private Integer level;
        private Integer trustLevel;
        private String badgeText;
        private String badgeColor;
        private String badgeStyle;

        public SimpleUserInfo(String id, String username, String nickname, String avatar,
                            Integer level, Integer trustLevel, String badgeText,
                            String badgeColor, String badgeStyle) {
            this.id = id;
            this.username = username;
            this.nickname = nickname;
            this.avatar = avatar;
            this.level = level;
            this.trustLevel = trustLevel;
            this.badgeText = badgeText;
            this.badgeColor = badgeColor;
            this.badgeStyle = badgeStyle;
        }

        // Getters
        public String getId() { return id; }
        public String getUsername() { return username; }
        public String getNickname() { return nickname; }
        public String getAvatar() { return avatar; }
        public Integer getLevel() { return level; }
        public Integer getTrustLevel() { return trustLevel; }
        public String getBadgeText() { return badgeText; }
        public String getBadgeColor() { return badgeColor; }
        public String getBadgeStyle() { return badgeStyle; }
    }
}
