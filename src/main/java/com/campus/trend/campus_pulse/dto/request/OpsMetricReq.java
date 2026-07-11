package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OpsMetricReq {
  @NotBlank
  @Size(min = 8, max = 128)
  private String idempotencyKey;

  @NotNull private LocalDateTime periodStart;
  @NotNull private LocalDateTime periodEnd;

  @NotBlank
  @Size(max = 100000)
  private String metricsJson;
}
