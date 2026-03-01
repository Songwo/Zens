package com.campus.trend.campus_pulse.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class PostUpdateReq {
    private String postId;
    private String title;
    private String content;
    private String coverImage;
    private String images; // Song：说明
    private String tags; // Song：说明
    private Long sectionId;
    private Integer isAnonymous;
    private String locationName;
}
