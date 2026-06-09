package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建系列请求DTO
 */
@Data
public class CreateSeriesReq {

    @NotBlank(message = "系列标题不能为空")
    private String title;

    private String description;

    private String coverImage;
}
