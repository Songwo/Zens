package com.campus.trend.campus_pulse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SubsiteEventResp {
    private Long id;
    private String eventId;
    private String source;
    private String eventType;
    private String userId;
    private String title;
    private String content;
    private String relatedId;
    private String severity;
    private String status;
    private String payloadJson;
    private LocalDateTime createdAt;
}
