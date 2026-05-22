package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class R2AbortReq {

    @NotBlank(message = "fileKey 不能为空")
    private String fileKey;

    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;
}
