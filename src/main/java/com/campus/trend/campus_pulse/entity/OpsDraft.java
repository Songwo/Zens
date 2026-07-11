package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ops_draft")
public class OpsDraft {
  @TableId(type = IdType.INPUT)
  private String id;

  private String idempotencyKey;
  private String planId;
  private String type;
  private String status;
  private String title;
  private String content;
  private Long sectionId;
  private String tags;
  private String coverImage;
  private String targetPostId;
  private String parentCommentId;
  private String postId;
  private String commentId;
  private String metadataJson;
  private String sourceService;
  private String approvedBy;
  private LocalDateTime approvedAt;
  private LocalDateTime publishedAt;
  private String failureReason;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
