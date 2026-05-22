package com.campus.trend.campus_pulse.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmailCheckResp(boolean exists, String username) {
}
