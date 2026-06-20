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
 * 子站事件账本：记录积分商城、CDK、抽奖等子系统回流到主站的业务事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_subsite_event")
public class SubsiteEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String source;

    private String eventType;

    private String userId;

    private String title;

    private String content;

    private String relatedId;

    private String severity;

    private String status;

    private String payloadJson;

    private LocalDateTime createdAt;
}
