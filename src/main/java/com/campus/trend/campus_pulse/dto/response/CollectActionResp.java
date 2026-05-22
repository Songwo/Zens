package com.campus.trend.campus_pulse.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectActionResp(boolean isCollected, String message) {

    public static CollectActionResp of(boolean isCollected) {
        return new CollectActionResp(isCollected, null);
    }

    public static CollectActionResp of(boolean isCollected, String message) {
        return new CollectActionResp(isCollected, message);
    }
}
