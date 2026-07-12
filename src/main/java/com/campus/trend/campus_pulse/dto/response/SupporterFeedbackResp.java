package com.campus.trend.campus_pulse.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SupporterFeedbackResp(
        Long id,
        String userId,
        String subject,
        String content,
        String status,
        String adminReply,
        String repliedBy,
        LocalDateTime repliedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
