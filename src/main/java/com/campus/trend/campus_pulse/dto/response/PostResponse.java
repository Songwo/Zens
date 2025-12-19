package com.campus.trend.campus_pulse.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子响应DTO - 包含作者信息和趋势分析数据
 * 用于解决前端无法显示帖子作者信息的问题
 */
@Data
@Accessors(chain = true)
public class PostResponse implements Serializable {

    // =================== 帖子基础信息 ===================
    private String id;
    private String userId;
    private String categoryId;
    private String title;
    private String content;
    private List<String> images;
    private String tags;
    private Integer isAnonymous;
    private String locationName;
    private Integer status;
    private String auditStatus;

    // =================== 统计数据 ===================
    private Integer viewCount;
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;
    private Double heatScore;

    // =================== 时间信息 ===================
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // =================== 用户交互状态 ===================
    @JsonProperty("isLiked")
    private Boolean isLiked;

    @JsonProperty("isCollected")
    private Boolean isCollected;

    // =================== 作者信息 (新增) ===================
    /**
     * 作者昵称
     * 如果isAnonymous=1，返回"匿名同学"
     * 如果isAnonymous=0，返回用户昵称
     */
    private String authorName;

    /**
     * 作者头像URL
     * 如果isAnonymous=1，返回null
     */
    private String authorAvatar;

    // =================== 智能分析数据 (新增) ===================
    /**
     * 情感分数 (0-1)
     */
    private BigDecimal sentimentScore;

    /**
     * 情感标签: positive(积极), neutral(中性), negative(消极)
     */
    private String sentimentLabel;

    /**
     * 分类名称 (便于前端展示)
     */
    private String categoryName;

    /**
     * 趋势热度等级: hot(热门), trending(上升), normal(普通)
     */
    private String trendLevel;

    /**
     * AI 生成的摘要 (新增)
     */
    private String summary;
}
