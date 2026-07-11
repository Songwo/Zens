package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OpsDecisionReq {
  @Size(max = 500)
  private String note;
}
