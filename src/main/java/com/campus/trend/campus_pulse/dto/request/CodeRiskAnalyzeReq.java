package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CodeRiskAnalyzeReq {

    @NotBlank(message = "代码片段不能为空")
    private String code;

    private String language;
}
