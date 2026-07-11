package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ops_approval")
public class OpsApproval {
  @TableId(type = IdType.INPUT)
  private String id;

  private String draftId;
  private String action;
  private String operatorId;
  private String note;
  private LocalDateTime createTime;
}
