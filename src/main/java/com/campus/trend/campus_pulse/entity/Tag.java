package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("sys_tag")
public class Tag implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * 类型 1:系统预设 2:用户生成
     */
    private Integer type;

    /**
     * 全站热度
     */
    private Integer heat;

    private LocalDateTime createTime;

    /**
     * 可见帖子数(冗余计数,由 TagHeatDecayTask 每小时校准)。
     * 迁移: sql/migrations/2026-06-13-add-tag-post-count.sql
     */
    @TableField("post_count")
    private Integer postCount;
}