package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("supporter_plan")
public class SupporterPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer priceCents;
    private String currency;
    private Integer durationDays;
    private String benefitsJson;
    private Boolean active;
    private Integer sortWeight;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
