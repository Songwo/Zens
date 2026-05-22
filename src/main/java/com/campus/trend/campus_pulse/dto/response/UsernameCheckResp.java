package com.campus.trend.campus_pulse.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsernameCheckResp(boolean available, String message) {

    public static UsernameCheckResp ok() {
        return new UsernameCheckResp(true, null);
    }

    public static UsernameCheckResp taken(String message) {
        return new UsernameCheckResp(false, message);
    }
}
