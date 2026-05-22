package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class R2InitReq {

    @NotBlank(message = "originalName 不能为空")
    private String originalName;

    @NotBlank(message = "mediaType 不能为空")
    private String mediaType;

    @NotNull(message = "sizeBytes 不能为空")
    @Min(value = 1, message = "sizeBytes 必须大于 0")
    private Long sizeBytes;

    private String sha256;

    private String bizType;

    private String bizId;

    @NotBlank(message = "mimeType 不能为空")
    private String mimeType;
}
