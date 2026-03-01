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
 * Song：版主申请表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("moderator_applications")
public class ModeratorApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private Long sectionId;

    private String reason;

    /**
     * Song：说明
     */
    private Integer status;

    private String reviewNote;

    private LocalDateTime createdAt;

    private LocalDateTime reviewedAt;
}
