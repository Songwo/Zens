package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationPreferenceReq {

    @NotNull(message = "邮件通知开关不能为空")
    private Boolean emailNotifyEnabled;
}
