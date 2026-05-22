package com.campus.trend.campus_pulse.dto.response;

import java.util.List;

/**
 * 当前登录用户的完整信息（/user/info）。保持和原 Map 返回相同字段名，
 * 前端 UserInfo 类型直接消费。
 */
public record UserProfileResp(
        String id,
        String username,
        String nickname,
        String avatar,
        String bio,
        String school,
        String major,
        Integer level,
        List<String> roles,
        long postCount,
        long followingCount,
        long followerCount,
        String profileCardTheme,
        String quickCardTheme,
        String profileCardBgUrl,
        String quickCardBgUrl
) {
}
