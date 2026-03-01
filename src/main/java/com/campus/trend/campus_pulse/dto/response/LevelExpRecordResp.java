package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LevelExpRecordResp {
    private Long id;
    private Integer expDelta;
    private String reason;
    private LocalDateTime createTime;
}
