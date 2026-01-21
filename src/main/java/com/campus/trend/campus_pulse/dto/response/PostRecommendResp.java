package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;
import java.util.List;

/**
 * 推荐帖子响应 DTO
 */
@Data
public class PostRecommendResp {
    private String id;
    private String title;
    private String summary;
    private String categoryName;
    private List<String> tags;
    private Integer viewCount;
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;
    private String authorName;
    private String authorAvatar;
    private String createTime;
    
    /**
     * 推荐理由 (e.g. "热门推荐", "基于你的兴趣", "看过此帖的人也看了")
     */
    private String recommendReason;
    
    private Boolean isLiked;
    private Boolean isCollected;
}
