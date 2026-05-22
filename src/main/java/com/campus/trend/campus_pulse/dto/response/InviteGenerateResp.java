package com.campus.trend.campus_pulse.dto.response;

/** 生成邀请码后的响应。 */
public record InviteGenerateResp(String code, String link, int expireDays) {
}
