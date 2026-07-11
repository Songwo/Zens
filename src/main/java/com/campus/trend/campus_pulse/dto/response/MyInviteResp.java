package com.campus.trend.campus_pulse.dto.response;

import java.util.List;

/** 我的邀请页面聚合响应。 */
public record MyInviteResp(
        List<InviteRecordResp> records,
        int total,
        int userLevel,
        int minLevel,
        boolean canGenerate,
        int maxPendingCodes
) {
}
