package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Song：站点统计接口
 */
@Slf4j
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final CommentMapper commentMapper;

    /**
     * Song：站点汇总统计
     * Song：说明
     */
    @GetMapping("/site")
    public Result<?> getSiteStats() {
        try {
            long totalPosts = postMapper.selectCount(
                    new LambdaQueryWrapper<Post>().eq(Post::getStatus, 1));

            long totalUsers = userMapper.selectCount(null);

            // Song：今日新帖
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            long todayPosts = postMapper.selectCount(
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getStatus, 1)
                            .ge(Post::getCreateTime, todayStart));

            // Song：说明
            long totalComments = 0;
            try {
                totalComments = commentMapper.selectCount(null);
            } catch (Exception ignored) {
            }

            Map<String, Object> data = new HashMap<>();
            data.put("totalPosts", totalPosts);
            data.put("totalUsers", totalUsers);
            data.put("totalComments", totalComments);
            data.put("todayPosts", todayPosts);

            return Result.success(data);
        } catch (Exception e) {
            log.error("获取站点统计失败", e);
            Map<String, Object> empty = new HashMap<>();
            empty.put("totalPosts", 0);
            empty.put("totalUsers", 0);
            empty.put("totalComments", 0);
            empty.put("todayPosts", 0);
            return Result.success(empty);
        }
    }
}
