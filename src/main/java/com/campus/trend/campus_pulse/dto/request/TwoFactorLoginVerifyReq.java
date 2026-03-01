package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TwoFactorLoginVerifyReq {

    @NotBlank(message = "二步验证票据不能为空")
    private String ticket;

    @NotBlank(message = "二步验证码不能为空")
    private String code;
}
