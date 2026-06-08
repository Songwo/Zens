package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

@Data
public class ShortLinkResolveResp {
    private String targetType;
    private String postId;
    private String commentId;
}
