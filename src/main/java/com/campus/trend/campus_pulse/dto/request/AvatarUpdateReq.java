package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AvatarUpdateReq {

    @NotBlank(message = "头像地址不能为空")
    private String avatarUrl;
}
