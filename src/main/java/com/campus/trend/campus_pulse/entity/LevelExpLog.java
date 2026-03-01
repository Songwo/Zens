package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 等级经验变更日志
 */
@Data
@Accessors(chain = true)
@TableName("sys_level_exp_log")
public class LevelExpLog implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String userId;

    /**
     * 本次经验变化值（正数）
     */
    private Integer expDelta;

    /**
     * 经验来源，如: 发帖 / 每日登录 / 被点赞
     */
    private String reason;

    private LocalDateTime createTime;
}
