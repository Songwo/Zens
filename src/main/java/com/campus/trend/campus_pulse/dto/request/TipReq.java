package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 打赏请求DTO
 */
@Data
public class TipReq {

    @NotBlank(message = "目标类型不能为空")
    private String targetType;

    @NotBlank(message = "目标ID不能为空")
    private String targetId;

    @NotNull(message = "打赏金额不能为空")
    @Min(value = 1, message = "打赏金额至少为1")
    private Integer amount;

    private String message;
}
