package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OpsCircuitReq {
  private boolean open;

  @Size(max = 500)
  private String reason;
}
