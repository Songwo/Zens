package com.campus.trend.campus_pulse.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class PostUpdateReq {
    private String postId;
    private String title;
    private String content;
    private String coverImage;
    private String images; // JSON String or comma separated
    private String tags; // "#Tag1 #Tag2"
    private Integer isAnonymous;
    private String locationName;
}
