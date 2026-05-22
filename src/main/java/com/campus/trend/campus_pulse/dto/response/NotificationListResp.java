package com.campus.trend.campus_pulse.dto.response;

import java.util.List;

public record NotificationListResp(
        List<NotificationResp> records,
        long total,
        long unreadCount
) {
}
