package com.campus.trend.campus_pulse.dto.response;

/** 搜索结果的单个用户条目。 */
public record UserSearchItemResp(
        String id,
        String username,
        String nickname,
        String avatar,
        String bio,
        String school,
        long postCount,
        long followerCount
) {
}
