package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@TableName(value = "sys_post", autoResultMap = true)
public class Post implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String userId;

    private Long sectionId;

    private String title;

    private String content;

    /**
     * Song：摘要
     */
    private String summary;

    /**
     * Song：封面图
     */
    private String coverImage;

    /**
     * Song：图片列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    private String tags;

    /**
     * Song：是否匿名 1:是 0:否
     */
    private Integer isAnonymous;

    private String locationName;

    /**
     * Song：情感分数
     */
    private BigDecimal sentimentScore;

    /**
     * Song：状态 1:正常 0:删除
     */
    private Integer status;

    /**
     * 审核/治理状态：DRAFT/APPROVED/REJECTED/DELETED。
     * DELETED 复用现有字段表示软删除，不额外改表。
     */
    private String auditStatus;

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
     * Song：置顶排序 (数字越小越靠前)
     */
    private Integer pinOrder;

    private LocalDateTime pinExpireAt;

    /**
     * Song：是否加精 0:否 1:是
     */
    private Integer isFeatured;

    // Song：统计数据
    private Integer viewCount;
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;

    /**
     * Song：是否有采纳答案
     */
    private Integer hasAdoptedAnswer;

    private Double heatScore;

    /**
     * Song：最后回复时间
     */
    private LocalDateTime lastReplyAt;

    /**
     * Song：用于列表排序
     */
    private LocalDateTime lastActivityAt;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 审核打回原因
     */
    private String rejectReason;

    /**
     * Song：当前用户是否已点赞
     */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonProperty("isLiked")
    private Boolean isLiked;

    /**
     * Song：当前用户是否已收藏
     */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonProperty("isCollected")
    private Boolean isCollected;
}
