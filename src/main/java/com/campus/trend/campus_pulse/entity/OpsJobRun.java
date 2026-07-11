package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ops_job_run")
public class OpsJobRun {
  @TableId(type = IdType.INPUT)
  private String id;

  private String jobType;
  private String idempotencyKey;
  private String draftId;
  private String serviceId;
  private String operatorId;
  private String status;
  private String detailJson;
  private String errorMessage;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
}
