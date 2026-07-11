package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class OpsPublishReq {
  @NotBlank
  @Size(min = 8, max = 128)
  private String idempotencyKey;
}
