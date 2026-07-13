package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SupporterVoucherImportReq(
        @NotNull(message = "额度不能为空")
        @Min(value = 30, message = "额度仅支持 30 或 50")
        @Max(value = 50, message = "额度仅支持 30 或 50")
        Integer quota,
        @NotEmpty(message = "兑换码列表不能为空")
        @Size(max = 500, message = "单次最多导入 500 个兑换码")
        List<@Valid @NotBlank(message = "兑换码不能为空")
                @Size(max = 256, message = "单个兑换码最长 256 字符") String> codes
) {
}
