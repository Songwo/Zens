package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupporterFeedbackCreateReq(
        @NotBlank(message = "反馈主题不能为空")
        @Size(min = 4, max = 100, message = "反馈主题需为 4 到 100 个字符")
        String subject,
        @NotBlank(message = "反馈内容不能为空")
        @Size(min = 10, max = 2000, message = "反馈内容需为 10 到 2000 个字符")
        String content
) {
}
