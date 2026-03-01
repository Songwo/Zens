package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知响应DTO
 */
@Data
public class NotificationResp {
    private Long id;
    private String userId;
    private String type;
    private String title;
    private String content;
    private String relatedId;
    private String relatedUserId;
    private String relatedUserNickname;
    private String relatedUserAvatar;
    private Integer isRead;
    private LocalDateTime createdAt;
}
