package com.campus.trend.campus_pulse.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostHeatUpdateItem {

    private String id;

    private Double heatScore;
}
