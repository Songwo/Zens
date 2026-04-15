package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class NotificationBatchReq {

    @NotEmpty(message = "通知ID列表不能为空")
    private List<Long> ids;
}
