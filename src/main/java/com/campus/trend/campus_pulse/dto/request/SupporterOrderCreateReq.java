package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupporterOrderCreateReq(
        @NotBlank @Size(max = 50) String planCode,
        @NotBlank @Size(min = 8, max = 100)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "只能包含字母、数字、下划线和短横线")
        String idempotencyKey) {
}
