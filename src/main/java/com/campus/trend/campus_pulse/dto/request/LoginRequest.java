package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "图形验证码ID不能为空")
    private String uuid;

    @NotBlank(message = "图形验证码不能为空")
    private String code;
}
