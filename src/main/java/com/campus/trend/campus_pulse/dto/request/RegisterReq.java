package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterReq {

    /**
     * Song：用户名（唯一标识，字母、数字、下划线 4-20位）
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度在 4-20 个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @Size(max = 20, message = "昵称长度不能超过 20 个字符")
    private String nickname;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在 6-20 个字符之间")
    private String password;

    @Pattern(regexp = "^(http|https)://.*$", message = "头像必须是合法的 URL", groups = UrlCheck.class)
    private String avatar;

    @Size(max = 30, message = "专业名称长度不能超过 30 个字符")
    private String major;

    @Min(value = 2000, message = "年级不能小于 2000")
    @Max(value = 2030, message = "年级不能大于 2030")
    private Integer grade;

    private Integer role;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;

    /**
     * Song：性别 0:未知 1:男 2:女
     */
    private Integer gender;

    @Size(max = 50, message = "学校名称长度不能超过 50 个字符")
    private String school;

    /** 邀请码（可选，管理员配置 campus.invite.required=true 时必填） */
    @Size(max = 20, message = "邀请码格式不正确")
    private String inviteCode;

    // Song：说明
    public interface UrlCheck {
    }
}
