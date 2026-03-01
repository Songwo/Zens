package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GithubLoginReq {

    @NotBlank(message = "GitHub授权码不能为空")
    private String code;

    @NotBlank(message = "GitHub状态参数不能为空")
    private String state;

    private boolean rememberMe = false;

    /**
     * Song：已开启二步验证时可直接携带一次性验证码，避免额外一步
     */
    private String twoFactorCode;
}
