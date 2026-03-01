package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.PostLikeService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Song：帖子点赞控制器
 * Song：处理帖子点赞、取消点赞、查询点赞状态等功能
 */
@Slf4j
@RestController
@RequestMapping("/post-like")
public class PostLikeController {

    private final PostLikeService postLikeService;

    @Autowired
    public PostLikeController(PostLikeService postLikeService) {
        this.postLikeService = postLikeService;
    }

    /**
     * Song：切换点赞状态（如果未点赞则点赞，如果已点赞则取消）
     *
     * Song：说明
     * Song：说明
     */
    @PostMapping("/{postId}/toggle")
    public Result<?> toggleLike(@PathVariable String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        boolean isLiked = postLikeService.toggleLike(postId, userId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("isLiked", isLiked);
        resultData.put("message", isLiked ? "点赞成功" : "取消点赞成功");

        return Result.success(resultData);
    }

    /**
     * Song：点赞状态
     *
     * Song：说明
     * Song：说明
     */
    @GetMapping("/{postId}/status")
    public Result<?> checkLikeStatus(@PathVariable String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        boolean isLiked = postLikeService.isLike(postId, userId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("isLiked", isLiked);

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
    public Result<?> getUserLikedPosts(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        IPage<Post> likedPostsPage = postLikeService.getPostLikeWithPage(authUser.getUser().getId(), page, pageSize);
        return Result.success(likedPostsPage);
    }
}
