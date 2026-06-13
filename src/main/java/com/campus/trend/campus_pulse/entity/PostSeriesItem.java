package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子系列关联表
 */
@Data
@Accessors(chain = true)
@TableName("post_series_items")
public class PostSeriesItem implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 系列ID
     */
    private Long seriesId;

    /**
     * 帖子ID
     */
    private String postId;

    /**
     * 排序序号
     */
    private Integer orderIndex;

    /**
     * 加入时间
     */
    private LocalDateTime addedAt;
}
