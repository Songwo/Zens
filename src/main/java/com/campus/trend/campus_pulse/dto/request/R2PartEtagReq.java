package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class R2PartEtagReq {

    @NotNull(message = "partNumber 不能为空")
    @Min(value = 1, message = "partNumber 必须大于 0")
    private Integer partNumber;

    @NotBlank(message = "etag 不能为空")
    private String etag;
}
