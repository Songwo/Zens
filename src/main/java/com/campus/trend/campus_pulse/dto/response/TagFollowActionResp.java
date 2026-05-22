package com.campus.trend.campus_pulse.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 标签关注动作/状态统一响应，isFollowing + success 语义共存；任一字段可 null。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TagFollowActionResp(
        Boolean isFollowing,
        Boolean success,
        String message
) {

    public static TagFollowActionResp following(boolean isFollowing, String message) {
        return new TagFollowActionResp(isFollowing, null, message);
    }

    public static TagFollowActionResp followingStatus(boolean isFollowing) {
        return new TagFollowActionResp(isFollowing, null, null);
    }

    public static TagFollowActionResp success(boolean success, String message) {
        return new TagFollowActionResp(null, success, message);
    }
}
