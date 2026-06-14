package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 信任等级变更日志（sys_trust_event）
 */
@Data
@Accessors(chain = true)
@TableName("sys_trust_event")
public class TrustEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private Integer oldLevel;

    private Integer newLevel;

    private String reason;

    /**
     * 触发时的指标快照（JSON）
     */
    private String metricsJson;

    private LocalDateTime createTime;
}
