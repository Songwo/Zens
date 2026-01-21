package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.ViewLog;
import com.campus.trend.campus_pulse.mapper.ViewLogMapper;
import com.campus.trend.campus_pulse.service.ViewLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 浏览日志服务实现类
 */
@Service
@Slf4j
public class ViewLogServiceImpl extends ServiceImpl<ViewLogMapper, ViewLog>
        implements ViewLogService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordView(String postId, String userId, String ip, String device) {
        ViewLog viewLog = new ViewLog()
                .setPostId(postId)
                .setUserId(userId)
                .setIp(ip)
                .setDevice(device)
                .setCreateTime(LocalDateTime.now());

        save(viewLog);
        log.debug("记录浏览: 帖子[{}], 用户[{}]", postId, userId != null ? userId : "游客");
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
        // 使用原生查询或者group by统计
        List<ViewLog> logs = lambdaQuery()
                .ge(startTime != null, ViewLog::getCreateTime, startTime)
                .list();

        // 按postId分组统计
        Map<String, Long> postViewCounts = logs.stream()
                .collect(Collectors.groupingBy(
                        ViewLog::getPostId,
                        Collectors.counting()));

        // 排序并取top N
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
    public List<ViewLog> getUserViewHistory(String userId, int limit) {
        return lambdaQuery()
                .eq(ViewLog::getUserId, userId)
                .orderByDesc(ViewLog::getCreateTime)
                .last("LIMIT " + limit)
                .list();
    }

    @Override
    public List<Map<String, Object>> getDailyViewStats(LocalDateTime startDate, LocalDateTime endDate) {
        List<ViewLog> logs = lambdaQuery()
                .ge(startDate != null, ViewLog::getCreateTime, startDate)
                .le(endDate != null, ViewLog::getCreateTime, endDate)
                .list();

        // 按日期分组统计
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

}
