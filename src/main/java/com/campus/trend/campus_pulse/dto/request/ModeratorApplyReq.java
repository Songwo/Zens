package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModeratorApplyReq {

    @NotNull(message = "请选择目标板块")
    private Long sectionId;

    @NotBlank(message = "请填写申请理由")
    @Size(min = 10, max = 500, message = "申请理由长度需在 10 到 500 个字符之间")
    private String reason;
}
