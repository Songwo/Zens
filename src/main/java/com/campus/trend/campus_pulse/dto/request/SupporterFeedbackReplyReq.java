package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupporterFeedbackReplyReq(
        @NotBlank(message = "回复内容不能为空")
        @Size(min = 2, max = 2000, message = "回复内容需为 2 到 2000 个字符")
        String reply,
        @Pattern(regexp = "^(ANSWERED|CLOSED)$", message = "回复状态仅支持 ANSWERED 或 CLOSED")
        String status
) {
}
