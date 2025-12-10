package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户画像响应DTO
 */
@Data
public class UserProfileResponse {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 信誉积分
     */
    private Integer reputation;

    /**
     * 社区贡献值
     */
    private Integer contributionVal;

    /**
     * 常活跃地点
     */
    private String activeRegion;

    /**
     * 偏好分类权重
     */
    private Map<String, Double> preferredCategories;

    /**
     * 发帖总数
     */
    private Integer totalPosts;

    /**
     * 获得的点赞数
     */
    private Integer totalLikesReceived;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;

}
