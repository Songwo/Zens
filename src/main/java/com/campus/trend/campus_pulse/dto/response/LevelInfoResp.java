package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LevelInfoResp {
    private Integer level;
    private Integer experience;
    private Integer currentLevelExp;
    private Integer nextLevelExp;
    private Double progress;
    private LocalDateTime lastUpgrade;
}
