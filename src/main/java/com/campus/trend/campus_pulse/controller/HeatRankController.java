package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 热度排行控制器 - 提供实时热度排行数据
 */
@Slf4j
@RestController
@RequestMapping("/heat-rank")
@RequiredArgsConstructor
public class HeatRankController {

    private final SysPostMapper postMapper;

    /**
     * 获取实时热度排行 TOP 10
     */
    @GetMapping("/top")
    public Result<?> getTopHeatRank() {
        try {
            // 查询热度最高的10个帖子
            LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysPost::getStatus, 1)
                    .orderByDesc(SysPost::getHeatScore)
                    .orderByDesc(SysPost::getViewCount)
                    .last("LIMIT 10");

            List<SysPost> hotPosts = postMapper.selectList(wrapper);

            // 转换为前端需要的格式
            List<Map<String, Object>> result = hotPosts.stream()
                    .map(post -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("postId", post.getId());
                        map.put("title", post.getTitle());
                        map.put("heatScore", post.getHeatScore() != null ? post.getHeatScore() : 0.0);
                        map.put("viewCount", post.getViewCount() != null ? post.getViewCount() : 0);
                        map.put("likeCount", post.getLikeCount() != null ? post.getLikeCount() : 0);
                        map.put("commentCount", post.getCommentCount() != null ? post.getCommentCount() : 0);
                        return map;
                    })
                    .collect(Collectors.toList());

            log.info("返回实时热度排行 {} 条", result.size());
            return Result.success(result);

        } catch (Exception e) {
            log.error("获取热度排行失败", e);
            return Result.success(Collections.emptyList());
        }
    }
}
