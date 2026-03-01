package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.dto.response.UserSimpleResp;
import com.campus.trend.campus_pulse.entity.Follow;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.FollowMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.FollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Song：关注服务实现
 */
@Slf4j
@Service
public class FollowServiceImpl implements FollowService {

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private com.campus.trend.campus_pulse.service.NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(String followerId, String followeeId) {
        // Song：不能关注自己
        if (followerId.equals(followeeId)) {
            throw new IllegalArgumentException("不能关注自己");
        }

        // Song：检查是否已关注
        if (isFollowing(followerId, followeeId)) {
            throw new IllegalArgumentException("已经关注过该用户");
        }

        // Song：检查被关注用户是否存在
        User followee = userMapper.selectById(followeeId);
        if (followee == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        // Song：创建关注记录
        Follow follow = Follow.builder()
                .followerId(followerId)
                .followeeId(followeeId)
                .createdAt(LocalDateTime.now())
                .build();

        followMapper.insert(follow);
        log.info("用户 {} 关注了用户 {}", followerId, followeeId);

        // Song：发送关注通知
        try {
            notificationService.sendFollowNotification(followeeId, followerId);
        } catch (Exception e) {
            log.error("发送关注通知失败: followerId={}, followeeId={}", followerId, followeeId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(String followerId, String followeeId) {
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId);

        int deleted = followMapper.delete(wrapper);
        if (deleted == 0) {
            throw new IllegalArgumentException("未关注该用户");
        }

        log.info("用户 {} 取消关注用户 {}", followerId, followeeId);
    }

    @Override
    public boolean isFollowing(String followerId, String followeeId) {
        return followMapper.checkFollowExists(followerId, followeeId) > 0;
    }

    @Override
    public List<UserSimpleResp> getFollowingList(String userId) {
        List<String> followingIds = followMapper.selectFollowingUserIds(userId);
        if (followingIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<User> users = userMapper.selectBatchIds(followingIds);
        return users.stream()
                .map(this::convertToSimpleResp)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserSimpleResp> getFollowerList(String userId) {
        List<String> followerIds = followMapper.selectFollowerUserIds(userId);
        if (followerIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<User> users = userMapper.selectBatchIds(followerIds);
        return users.stream()
                .map(this::convertToSimpleResp)
                .collect(Collectors.toList());
    }

    @Override
    public long getFollowingCount(String userId) {
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowerId, userId);
        return followMapper.selectCount(wrapper);
    }

    @Override
    public long getFollowerCount(String userId) {
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFolloweeId, userId);
        return followMapper.selectCount(wrapper);
    }

    private UserSimpleResp convertToSimpleResp(User user) {
        UserSimpleResp resp = new UserSimpleResp();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setAvatar(user.getAvatar());
        resp.setLevel(user.getLevel());
        return resp;
    }
}
