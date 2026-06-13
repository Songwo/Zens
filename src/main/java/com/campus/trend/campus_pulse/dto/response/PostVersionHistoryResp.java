package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostVersionHistoryResp {
    private Long id;
    private String postId;
    private Integer versionNo;
    private String editorId;
    private String editorName;
    private String title;
    private String content;
    private String tags;
    private Long sectionId;
    private String coverImage;
    private String changeSummary;
    private LocalDateTime createdAt;
}
