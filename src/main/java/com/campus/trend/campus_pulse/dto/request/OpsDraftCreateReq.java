package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class OpsDraftCreateReq {
  @NotBlank
  @Size(min = 8, max = 128)
  private String idempotencyKey;

  @Size(max = 64)
  private String planId;

  @NotBlank
  @Pattern(regexp = "POST|COMMENT")
  private String type;

  @Size(max = 200)
  private String title;

  @NotBlank
  @Size(max = 100000)
  private String content;

  private Long sectionId;

  @Size(max = 500)
  private String tags;

  @Size(max = 1000)
  private String coverImage;

  @Size(max = 64)
  private String targetPostId;

  @Size(max = 64)
  private String parentCommentId;

  @Size(max = 10000)
  private String metadataJson;
}
