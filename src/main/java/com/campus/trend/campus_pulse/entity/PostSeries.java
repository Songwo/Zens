package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子系列表
 */
@Data
@Accessors(chain = true)
@TableName("post_series")
public class PostSeries implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 创建者ID
     */
    private String userId;

    /**
     * 系列标题
     */
    private String title;

    /**
     * 系列描述
     */
    private String description;

    /**
     * 封面图
     */
    private String coverImage;

    /**
     * 状态 1=发布 0=草稿
     */
    private Integer status;

    /**
     * 帖子数量
     */
    private Integer postCount;

    /**
     * 总浏览量
     */
    private Integer viewCount;

    /**
     * 总点赞数
     */
    private Integer likeCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
