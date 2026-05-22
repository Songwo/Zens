package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知响应DTO。category 由后端根据 type 推导，便于前端做视觉分组（social / security / system）。
 */
@Data
public class NotificationResp {
    private Long id;
    private String userId;
    private String type;
    /** SOCIAL / SECURITY / SYSTEM */
    private String category;
    private String title;
    private String content;
    private String relatedId;
    private String relatedUserId;
    private String relatedUserNickname;
    private String relatedUserAvatar;
    private Integer isRead;
    private LocalDateTime createdAt;
}
