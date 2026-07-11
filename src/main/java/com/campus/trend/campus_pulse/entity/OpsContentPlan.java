package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ops_content_plan")
public class OpsContentPlan {
  @TableId(type = IdType.INPUT)
  private String id;

  private String idempotencyKey;
  private String topic;
  private String title;
  private String brief;
  private String status;
  private LocalDateTime scheduledAt;
  private String metadataJson;
  private String createdBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
