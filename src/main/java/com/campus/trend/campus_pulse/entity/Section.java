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
 * Song：板块表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sections")
public class Section {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Song：板块名称
     */
    private String name;

    /**
     * Song：板块描述
     */
    private String description;

    /**
     * Song：图标
     */
    private String icon;

    /**
     * Song：排序权重
     */
    private Integer sortOrder;

    /**
     * Song：状态：1启用 0禁用
     */
    private Integer status;

    /**
     * Song：是否支持答案采纳 0=否 1=是（如"答疑解惑"板块）
     */
    private Integer allowAdoption;

    /**
     * Song：创建时间
     */
    private LocalDateTime createdAt;
}
