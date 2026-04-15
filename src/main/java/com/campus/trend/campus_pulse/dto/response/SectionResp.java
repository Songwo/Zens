package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Song：说明
 */
@Data
public class SectionResp {
    private Long id;
    private String name;
    private String description;
    private String icon;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private Long postCount;
    private Long todayCount;
    private Long heatScore;
}
