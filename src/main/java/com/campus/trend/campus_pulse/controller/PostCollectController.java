package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.PostCollectService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Song：帖子收藏控制器
 * Song：处理帖子收藏、取消收藏、查询收藏状态等功能
 */
@Slf4j
@RestController
@RequestMapping("/post-collect")
public class PostCollectController {

    private final PostCollectService postCollectService;

    @Autowired
    public PostCollectController(PostCollectService postCollectService) {
        this.postCollectService = postCollectService;
    }

    /**
     * Song：切换收藏状态（如果未收藏则收藏，如果已收藏则取消）
     *
     * Song：说明
     * Song：说明
     */
    @PostMapping("/{postId}/toggle")
    public Result<?> toggleCollect(@PathVariable String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        boolean isCollected = postCollectService.toggleCollect(postId, userId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("isCollected", isCollected);
        resultData.put("message", isCollected ? "收藏成功" : "取消收藏成功");

        return Result.success(resultData);
    }

    /**
     * Song：检查当前用户是否收藏了指定帖子
     *
     * Song：说明
     * Song：说明
     */
    @GetMapping("/{postId}/status")
    public Result<?> checkCollectStatus(@PathVariable String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        boolean isCollected = postCollectService.isCollect(postId, userId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("isCollected", isCollected);

        return Result.success(resultData);
    }

    /**
     * Song：说明
     *
     * Song：说明
     * Song：说明
     * Song：说明
     */
    @GetMapping("/user")
    public Result<?> getUserCollectedPosts(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        IPage<Post> collectedPostsPage = postCollectService.getPostCollectWithPage(authUser.getUser().getId(), page, pageSize);
        return Result.success(collectedPostsPage);
    }
}
