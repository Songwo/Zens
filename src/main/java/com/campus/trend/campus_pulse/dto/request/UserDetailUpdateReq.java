package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UserDetailUpdateReq {

    @NotBlank
    @Size(max = 20, message = "昵称长度不能超过 20 个字符")
    private String nickname;

    @Size(max = 200, message = "个人简介长度不能超过 200 个字符")
    private String bio;

    @NotBlank
    @Pattern(regexp = "^(http|https)://.*$", message = "头像必须是合法的 URL", groups = RegisterReq.UrlCheck.class)
    private String avatar;

    @NotBlank
    @Size(max = 30, message = "专业名称长度不能超过 30 个字符")
    private String major;

    @Min(value = 2000, message = "入学年份不能小于 2000")
    @Max(value = 2030, message = "入学年份不能大于 2030")
    private int enrollmentYear;

    /**
     * Song：性别 0:未知 1:男 2:女
     */
    private Integer gender;

    @Size(max = 50, message = "学校名称长度不能超过 50 个字符")
    private String school;

    @NotBlank
    private String interestTags;

    @Size(max = 32, message = "个人资料卡片主题长度不能超过 32 个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_-]{0,32}$", message = "个人资料卡片主题格式非法")
    private String profileCardTheme;

    @Size(max = 32, message = "预览卡片主题长度不能超过 32 个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_-]{0,32}$", message = "预览卡片主题格式非法")
    private String quickCardTheme;

    @Size(max = 500, message = "个人资料背景图链接长度不能超过 500 个字符")
    @Pattern(
            regexp = "^$|^(https?://[^\"'\\s]+|/uploads/[^\"'\\s]+)$",
            message = "个人资料背景图链接格式非法")
    private String profileCardBgUrl;

    @Size(max = 500, message = "预览卡片背景图链接长度不能超过 500 个字符")
    @Pattern(
            regexp = "^$|^(https?://[^\"'\\s]+|/uploads/[^\"'\\s]+)$",
            message = "预览卡片背景图链接格式非法")
    private String quickCardBgUrl;

}
