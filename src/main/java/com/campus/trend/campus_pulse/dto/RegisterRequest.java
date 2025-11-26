package com.campus.trend.campus_pulse.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^[0-9]{6,20}$", message = "学号必须为 6-20 位数字")
    private String username;

    @Size(max = 20, message = "昵称长度不能超过 20 个字符")
    private String nickname;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在 6-20 个字符之间")
    private String password;

    @Pattern(regexp = "^(http|https)://.*$", message = "头像必须是合法的 URL",
            groups = UrlCheck.class)
    private String avatar;

    @Size(max = 30, message = "专业名称长度不能超过 30 个字符")
    private String major;

    @Min(value = 2000, message = "年级不能小于 2000")
    @Max(value = 2030, message = "年级不能大于 2030")
    private Integer grade;

    // 用于可选的 URL 验证分组
    public interface UrlCheck {}
}
