package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportManageResp {

    private String id;
    private String targetType;
    private String targetId;
    private String targetTitle;
    private String targetPreview;
    private Long sectionId;
    private String sectionName;
    private String reason;
    private String details;
    private String reporterId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
