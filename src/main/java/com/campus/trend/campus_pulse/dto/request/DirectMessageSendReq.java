package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DirectMessageSendReq {

    @NotBlank(message = "接收者不能为空")
    private String receiverId;

    @NotBlank(message = "消息内容不能为空")
    private String content;
}
