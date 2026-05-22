package com.campus.trend.campus_pulse.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 点赞动作 / 状态响应；状态查询时 message 为 null，序列化自动省略。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LikeActionResp(boolean isLiked, String message) {

    public static LikeActionResp of(boolean isLiked) {
        return new LikeActionResp(isLiked, null);
    }

    public static LikeActionResp of(boolean isLiked, String message) {
        return new LikeActionResp(isLiked, message);
    }
}
