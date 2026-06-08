package com.campus.trend.campus_pulse.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage implements Serializable {
    private String userId;
    private String type;
    private String title;
    private String content;
    private String relatedId;
    private String relatedUserId;
}
