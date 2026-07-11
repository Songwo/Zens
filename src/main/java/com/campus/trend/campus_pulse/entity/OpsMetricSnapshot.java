package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ops_metric_snapshot")
public class OpsMetricSnapshot {
  @TableId(type = IdType.INPUT)
  private String id;

  private String idempotencyKey;
  private LocalDateTime periodStart;
  private LocalDateTime periodEnd;
  private String metricsJson;
  private String sourceService;
  private LocalDateTime createTime;
}
