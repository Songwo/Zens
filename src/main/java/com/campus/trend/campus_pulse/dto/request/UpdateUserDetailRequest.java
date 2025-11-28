package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdateUserDetailRequest {

    @NotBlank
    @Size(max = 20, message = "昵称长度不能超过 20 个字符")
    private String nickname;

    @NotBlank
    @Pattern(regexp = "^(http|https)://.*$", message = "头像必须是合法的 URL",
            groups = RegisterRequest.UrlCheck.class)
    private String avatar;

    @NotBlank
    @Size(max = 30, message = "专业名称长度不能超过 30 个字符")
    private String major;

    @Min(value = 2000, message = "年级不能小于 2000")
    @Max(value = 2030, message = "年级不能大于 2030")
    private int grade;

    @NotBlank
    private  String interestTags;


}
