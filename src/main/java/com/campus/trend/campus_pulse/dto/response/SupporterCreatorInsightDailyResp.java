package com.campus.trend.campus_pulse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupporterCreatorInsightDailyResp {
    private LocalDate date;
    private long views;
    private long likes;
    private long collects;
    private long comments;
    private long avgDwellSec;
}
