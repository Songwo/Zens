package com.campus.trend.campus_pulse.dto.request;

import lombok.Data;

@Data
public class WebVitalMetricReq {
    private String name;
    private Double value;
    private String rating;
    private String route;
    private String navigationType;
    private String userAgent;
    private Long timestamp;
}
