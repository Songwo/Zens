package com.campus.trend.campus_pulse.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/** 邀请人视角下的邀请记录条目；被邀请人信息可能为空。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InviteRecordResp(
        String id,
        String code,
        String link,
        Integer status,
        Integer maxUses,
        Integer usedCount,
        LocalDateTime expireTime,
        LocalDateTime createTime,
        String remark,
        Invitee invitee
) {

    public record Invitee(
            String id,
            String nickname,
            String avatar,
            Integer level,
            LocalDateTime createTime
    ) {
    }
}
