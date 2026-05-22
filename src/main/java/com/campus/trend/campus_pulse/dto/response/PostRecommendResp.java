package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class PostRecommendResp {
    private String id;
    private String title;
    private String summary;
    private String sectionName;
    private List<String> tags;
    private Integer viewCount;
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;
    private String authorName;
    private String authorAvatar;
    private String createTime;
    
    private String recommendReason;
    
    private Boolean isLiked;
    private Boolean isCollected;
}
