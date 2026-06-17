package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.BatchUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 批量用户查询服务实现
 * 解决N+1查询问题，提升列表页性能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchUserServiceImpl implements BatchUserService {

    private final UserMapper userMapper;

    @Override
    public Map<String, User> batchGetUsers(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 去重
        List<String> distinctIds = userIds.stream().distinct().toList();

        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 批量查询（利用 MyBatis-Plus 的 IN 查询优化）
        List<User> users = userMapper.selectList(
            new LambdaQueryWrapper<User>()
                .in(User::getId, distinctIds)
                .eq(User::getStatus, 1)  // 只返回正常状态用户
        );

        log.debug("批量查询用户: 请求{}个, 返回{}个", distinctIds.size(), users.size());

        return users.stream()
            .collect(Collectors.toMap(
                User::getId,
                user -> user,
                (existing, replacement) -> existing  // 防止重复key
            ));
    }

    @Override
    @Cacheable(value = "user:batch:simple", key = "#userIds.hashCode()", unless = "#result.isEmpty()")
    public Map<String, SimpleUserInfo> batchGetSimpleUsers(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 去重
        List<String> distinctIds = userIds.stream().distinct().toList();

        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 只查询必要字段，减少网络传输
        List<User> users = userMapper.selectList(
            new LambdaQueryWrapper<User>()
                .select(
                    User::getId,
                    User::getUsername,
                    User::getNickname,
                    User::getAvatar,
                    User::getLevel,
                    User::getTrustLevel,
                    User::getBadgeText,
                    User::getBadgeColor,
                    User::getBadgeStyle
                )
                .in(User::getId, distinctIds)
                .eq(User::getStatus, 1)
        );

        log.debug("批量查询精简用户信息: 请求{}个, 返回{}个", distinctIds.size(), users.size());

        return users.stream()
            .collect(Collectors.toMap(
                User::getId,
                user -> new SimpleUserInfo(
                    user.getId(),
                    user.getUsername(),
                    user.getNickname(),
                    user.getAvatar(),
                    user.getLevel(),
                    user.getTrustLevel(),
                    user.getBadgeText(),
                    user.getBadgeColor(),
                    user.getBadgeStyle()
                ),
                (existing, replacement) -> existing
            ));
    }
}
