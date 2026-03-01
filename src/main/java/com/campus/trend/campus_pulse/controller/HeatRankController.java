package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Song：热度排行控制器 - 提供实时热度排行数据
 */
@Slf4j
@RestController
@RequestMapping("/heat-rank")
@RequiredArgsConstructor
public class HeatRankController {

    private final PostMapper postMapper;

    /**
     * Song：说明
     */
    @GetMapping("/top")
    public Result<?> getTopHeatRank() {
        try {
            // Song：查询热度最高的10个帖子
            LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Post::getStatus, 1)
                    .orderByDesc(Post::getHeatScore)
                    .orderByDesc(Post::getViewCount)
                    .last("LIMIT 10");

            List<Post> hotPosts = postMapper.selectList(wrapper);

            // Song：转换为前端需要的格式
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
