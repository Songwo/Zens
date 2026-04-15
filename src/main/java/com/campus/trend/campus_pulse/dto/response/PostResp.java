package com.campus.trend.campus_pulse.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Song：说明
 * Song：用于解决前端无法显示帖子作者信息的问题
 */
@Data
@Accessors(chain = true)
public class PostResp implements Serializable {

    // Song：=================== 帖子基础信息 ===================
    private String id;
    private String userId;
    private Long sectionId;
    private String title;
    private String content;
    private String coverImage;
    private List<String> images;
    private String tags;
    private Integer isAnonymous;

    /**
     * Song：旧的置顶字段（已废弃）
     */
    @Deprecated
    private Integer isPinned;

    /**
     * Song：全局置顶 0:否 1:是
     */
    private Integer globalPin;

    /**
     * Song：板块置顶 0:否 1:是
     */
    private Integer categoryPin;

    /**
     * Song：置顶排序
     */
    private Integer pinOrder;

    /**
     * Song：置顶过期时间
     */
    private LocalDateTime pinExpireAt;

    private String locationName;
    private Integer status;
    private String auditStatus;
    private String rejectReason;

    // Song：=================== 统计数据 ===================
    private Integer viewCount;
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;
    private Double heatScore;

    // Song：=================== 时间信息 ===================
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * Song：最后回复时间
     */
    private LocalDateTime lastReplyAt;

    /**
     * Song：最后活跃时间（用于排序）
     */
    private LocalDateTime lastActivityAt;

    /**
     * Song：是否加精 0:否 1:是
     */
    private Integer isFeatured;

    // Song：=================== 用户交互状态 ===================
    @JsonProperty("isLiked")
    private Boolean isLiked;

    @JsonProperty("isCollected")
    private Boolean isCollected;

    // Song：=================== 作者信息 (新增) ===================
    /**
     * Song：作者昵称
     * Song：说明
     * Song：说明
     */
    private String authorName;

    /**
     * Song：说明
     * Song：说明
     */
    private String authorAvatar;

    /**
     * Song：作者角色列表
     */
    private List<String> authorRoles;

    // Song：=================== 智能分析数据 (新增) ===================
    /**
     * Song：情感分数 (0-1)
     */
    private BigDecimal sentimentScore;

    /**
     * Song：说明
     */
    private String sentimentLabel;

    /**
     * Song：板块名称 (便于前端展示)
     */
    private String sectionName;

    /**
     * Song：说明
     */
    private String trendLevel;

    /**
     * Song：智能 生成的摘要 (新增)
     */
    private String summary;
}
