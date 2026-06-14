package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.response.ViewHistoryDto;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.ViewLog;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.ViewLogMapper;
import com.campus.trend.campus_pulse.service.ViewLogService;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.service.PostEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Song：浏览日志服务实现类
 */
@Service
@Slf4j
public class ViewLogServiceImpl extends ServiceImpl<ViewLogMapper, ViewLog>
        implements ViewLogService {


    private final LevelService levelService;
    private final StringRedisTemplate stringRedisTemplate;
    private final PostMapper postMapper;
    private final PostEventService postEventService;
    private final com.campus.trend.campus_pulse.service.post.PostCacheManager postCacheManager;
    private final com.campus.trend.campus_pulse.mapper.UserMapper userMapper;

    public ViewLogServiceImpl(LevelService levelService, StringRedisTemplate stringRedisTemplate, PostMapper postMapper,
            PostEventService postEventService,
            com.campus.trend.campus_pulse.service.post.PostCacheManager postCacheManager,
            com.campus.trend.campus_pulse.mapper.UserMapper userMapper) {
        this.levelService = levelService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.postMapper = postMapper;
        this.postEventService = postEventService;
        this.postCacheManager = postCacheManager;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordView(String postId, String userId, String ip, String device) {
        LocalDateTime now = LocalDateTime.now();
        ViewLog viewLog = new ViewLog()
                .setPostId(postId)
                .setUserId(userId)
                .setIp(ip)
                .setDevice(device)
                .setCreateTime(now);

        save(viewLog);
        // Song：浏览量累计与活跃时间刷新放在埋点接口，避免阻塞帖子详情查询
        postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .set(Post::getLastActivityAt, now)
                .setSql("view_count = view_count + 1"));
        log.debug("记录浏览: 帖子[{}], 用户[{}]", postId, userId != null ? userId : "游客");

        Post post = postMapper.selectById(postId);
        if (post != null) {
            invalidatePostFeedCache(post.getSectionId(), postId);
            try {
                postEventService.pushPostViewed(postId, post.getSectionId(), post.getViewCount(), post.getLastActivityAt());
            } catch (Exception e) {
                log.debug("推送浏览事件失败: postId={}, err={}", postId, e.getMessage());
            }
        }

        // Song：每日浏览经验 +1（上限5次/天）
        if (userId != null) {
            try {
                String viewExpKey = "exp:view:" + userId + ":" + LocalDate.now();
                String countStr = stringRedisTemplate.opsForValue().get(viewExpKey);
                int count = countStr != null ? Integer.parseInt(countStr) : 0;
                if (count < 5) {
                    levelService.addExperience(userId, 1, "每日浏览帖子");
                    stringRedisTemplate.opsForValue().set(viewExpKey, String.valueOf(count + 1), 1, TimeUnit.DAYS);
                }
            } catch (Exception e) {
                log.warn("每日浏览经验发放失败: {}", e.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordHeartbeat(String postId, String userId, int durationMs) {
        if (postId == null || durationMs <= 0) {
            return;
        }
        // Song：同一帖子 5 秒内的心跳去重，避免前端定时器误差导致重复累加
        if (userId != null) {
            String dedupeKey = "view:heartbeat:" + userId + ":" + postId;
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(dedupeKey, "1", 5, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(acquired)) {
                return;
            }
        }

        // Song：写入浏览日志（带停留时长），不重复 +1 view_count，避免刷量
        ViewLog viewLog = new ViewLog()
                .setPostId(postId)
                .setUserId(userId)
                .setDurationMs(durationMs)
                .setCreateTime(LocalDateTime.now());
        save(viewLog);

        // Song：累加用户累计阅读时长（秒），用于 TL 计算
        if (userId != null) {
            try {
                int sec = durationMs / 1000;
                if (sec > 0) {
                    userMapper.update(null,
                            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.campus.trend.campus_pulse.entity.User>()
                                    .eq(com.campus.trend.campus_pulse.entity.User::getId, userId)
                                    .setSql("read_time_sec = read_time_sec + " + sec));
                }
            } catch (Exception e) {
                log.debug("累加用户阅读时长失败 userId={}, err={}", userId, e.getMessage());
            }
        }

        // Song：累加帖子平均阅读时长（秒），用于热度公式加权
        try {
            int sec = durationMs / 1000;
            if (sec > 0) {
                postMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Post>()
                                .eq(Post::getId, postId)
                                .setSql("avg_dwell_sec = (avg_dwell_sec * view_count + " + sec + ") / GREATEST(view_count, 1)"));
            }
        } catch (Exception e) {
            log.debug("更新帖子平均阅读时长失败 postId={}, err={}", postId, e.getMessage());
        }
    }

    @Override
    public long getViewCount(String postId, LocalDateTime startTime, LocalDateTime endTime) {
        Long count = lambdaQuery()
                .eq(ViewLog::getPostId, postId)
                .ge(startTime != null, ViewLog::getCreateTime, startTime)
                .le(endTime != null, ViewLog::getCreateTime, endTime)
                .count();
        return count != null ? count : 0;
    }

    @Override
    public long getTotalViewCount(String postId) {
        Long count = lambdaQuery()
                .eq(ViewLog::getPostId, postId)
                .count();
        return count != null ? count : 0;
    }

    @Override
    public List<Map<String, Object>> getHotPostsByViews(LocalDateTime startTime, int limit) {
        List<ViewLog> logs = lambdaQuery()
                .ge(startTime != null, ViewLog::getCreateTime, startTime)
                .list();

        Map<String, Long> postViewCounts = logs.stream()
                .collect(Collectors.groupingBy(
                        ViewLog::getPostId,
                        Collectors.counting()));

        return postViewCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("postId", entry.getKey());
                    map.put("viewCount", entry.getValue());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<ViewHistoryDto> getUserViewHistory(String userId, int limit) {
        return baseMapper.selectUserViewHistory(userId, limit);
    }

    @Override
    public Map<String, Object> getUserViewHistoryPaged(String userId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safePageSize;

        List<ViewHistoryDto> records = baseMapper.selectUserViewHistoryPaged(userId, offset, safePageSize);
        long total = baseMapper.countDistinctViewedPosts(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("current", safePage);
        result.put("size", safePageSize);
        result.put("pages", total == 0 ? 0 : (long) Math.ceil((double) total / safePageSize));

        return result;
    }

    @Override
    public List<Map<String, Object>> getDailyViewStats(LocalDateTime startDate, LocalDateTime endDate) {
        List<ViewLog> logs = lambdaQuery()
                .ge(startDate != null, ViewLog::getCreateTime, startDate)
                .le(endDate != null, ViewLog::getCreateTime, endDate)
                .list();

        // Song：按日期分组统计
        Map<String, Long> dailyStats = logs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getCreateTime().toLocalDate().toString(),
                        Collectors.counting()));

        return dailyStats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("date", entry.getKey());
                    map.put("count", entry.getValue());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Long> getDeviceDistribution() {
        List<ViewLog> logs = list();
        return logs.stream()
                .filter(log -> log.getDevice() != null)
                .collect(Collectors.groupingBy(
                        ViewLog::getDevice,
                        Collectors.counting()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long cleanOldLogs(int daysToKeep) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(daysToKeep);

        long count = lambdaQuery()
                .lt(ViewLog::getCreateTime, cutoffTime)
                .count();

        if (count > 0) {
            remove(new LambdaQueryWrapper<ViewLog>()
                    .lt(ViewLog::getCreateTime, cutoffTime));

            log.info("清理了 {} 条超过 {} 天的浏览日志", count, daysToKeep);
        }

        return count;
    }

    private void invalidatePostFeedCache(Long sectionId, String postId) {
        postCacheManager.invalidatePostCaches(sectionId, postId);
    }

}
