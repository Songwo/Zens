package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DirectMessageResp {
    private Long id;
    private String senderId;
    private String receiverId;
    private String content;
    private Integer isRead;
    private LocalDateTime createdAt;
    private Boolean self;
}
