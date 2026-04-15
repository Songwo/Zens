package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModeratorReviewReq {

    @Size(max = 500, message = "审核备注长度不能超过 500 个字符")
    private String reviewNote;
}
