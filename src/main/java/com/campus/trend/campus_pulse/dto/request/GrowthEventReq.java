package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class GrowthEventReq {
    @NotBlank @Size(max = 40)
    private String eventName;
    @NotBlank @Size(max = 64)
    private String anonymousId;
    @NotBlank @Size(max = 64)
    private String sessionId;
    @Size(max = 255)
    private String route;
    @Size(max = 40)
    private String source;
    private Map<String, Object> properties;
}
