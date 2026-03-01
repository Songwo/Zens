package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.UserSimpleResp;

import java.util.List;

/**
 * Song：关注服务接口
 */
public interface FollowService {

    /**
     * Song：关注用户
     */
    void follow(String followerId, String followeeId);

    /**
     * Song：取消关注
     */
    void unfollow(String followerId, String followeeId);

    /**
     * Song：检查是否已关注
     */
    boolean isFollowing(String followerId, String followeeId);

    /**
     * Song：获取关注列表
     */
    List<UserSimpleResp> getFollowingList(String userId);

    /**
     * Song：获取粉丝列表
     */
    List<UserSimpleResp> getFollowerList(String userId);

    /**
     * Song：获取关注数量
     */
    long getFollowingCount(String userId);

    /**
     * Song：获取粉丝数量
     */
    long getFollowerCount(String userId);
}
