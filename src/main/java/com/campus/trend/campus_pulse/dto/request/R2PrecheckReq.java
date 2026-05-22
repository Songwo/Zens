package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class R2PrecheckReq {

    @NotBlank(message = "sha256 不能为空")
    private String sha256;

    @NotNull(message = "sizeBytes 不能为空")
    @Min(value = 1, message = "sizeBytes 必须大于 0")
    private Long sizeBytes;
}
