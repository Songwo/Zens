package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OpsPlanCreateReq {
  @NotBlank
  @Size(min = 8, max = 128)
  private String idempotencyKey;

  @NotBlank
  @Size(max = 200)
  private String topic;

  @Size(max = 200)
  private String title;

  @Size(max = 5000)
  private String brief;

  private LocalDateTime scheduledAt;

  @Size(max = 10000)
  private String metadataJson;
}
