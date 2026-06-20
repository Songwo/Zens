package com.campus.trend.campus_pulse.dto.response;

import com.campus.trend.campus_pulse.dto.media.MediaObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
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
    private String postType;
    private LocalDateTime commentDeadline;
    private Integer commentOncePerUser;

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
    private Integer hasAdoptedAnswer;
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
     */
    private String authorName;

    private String authorAvatar;

    /**
     * Song：作者角色列表
     */
    private List<String> authorRoles;

    /**
     * 作者徽章文字（管理员授予的纯文字 flair），空表示无
     */
    private String authorBadgeText;
    private String authorBadgeColor;
    private String authorBadgeStyle;
    /**
     * 作者信任等级 0-4（TL0新人/TL1基础/TL2成员/TL3常客/TL4领袖）
     */
    private Integer authorTrustLevel;

    // Song：=================== 智能分析数据 (新增) ===================
    /**
     * Song：情感分数 (0-1)
     */
    private BigDecimal sentimentScore;

    private String sentimentLabel;

    /**
     * Song：板块名称 (便于前端展示)
     */
    private String sectionName;

    /**
     * Song：当前板块是否支持答案采纳 0=否 1=是（取自 sections.allow_adoption，前端据此决定是否显示采纳按钮，替代写死的 sectionId==11）
     */
    private Integer allowAdoption;

    private String trendLevel;

    /**
     * Song：智能 生成的摘要 (新增)
     */
    private String summary;

    /**
     * Song：新版媒体列表（来自 sys_post_media）。与 images 字段并存，老前端继续用 images，新前端读 mediaList。
     */
    private List<MediaObject> mediaList;
}
