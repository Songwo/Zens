package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModeratorApplicationResp {

    private Long id;
    private String userId;
    private String applicantUsername;
    private String applicantNickname;
    private String applicantAvatar;
    private String applicantEmail;
    private Integer applicantLevel;
    private String applicantRole;
    private Long sectionId;
    private String sectionName;
    private String sectionDescription;
    private Integer sectionStatus;
    private String reason;
    private Integer status;
    private String reviewNote;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
