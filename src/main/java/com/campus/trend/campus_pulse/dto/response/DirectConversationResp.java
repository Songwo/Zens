package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DirectConversationResp {
    private String conversationId;
    private String peerId;
    private String peerName;
    private String peerAvatar;
    private String peerBadgeText;
    private String peerBadgeColor;
    private String peerBadgeStyle;
    private String lastMessage;
    private LocalDateTime lastTime;
    private Long unreadCount;
}
