package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Song：发展历程 / 版本日志表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_changelog")
public class Changelog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String version;

    private String title;

    private String content;

    /**
     * Song：时间标签，如 "2026-02-26"
     */
    private String timestamp;

    /**
     * Song：说明
     */
    private Integer status;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
