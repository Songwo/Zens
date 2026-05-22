package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.LikeActionResp;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.PostLikeService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/post-like")
public class PostLikeController {

    private final PostLikeService postLikeService;

    @PostMapping("/{postId}/toggle")
    public Result<LikeActionResp> toggleLike(@PathVariable String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        boolean isLiked = postLikeService.toggleLike(postId, authUser.getUser().getId());
        return Result.success(LikeActionResp.of(isLiked, isLiked ? "点赞成功" : "取消点赞成功"));
    }

    @GetMapping("/{postId}/status")
    public Result<LikeActionResp> checkLikeStatus(@PathVariable String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        boolean isLiked = postLikeService.isLike(postId, authUser.getUser().getId());
        return Result.success(LikeActionResp.of(isLiked));
    }

    @GetMapping("/user")
    public Result<IPage<Post>> getUserLikedPosts(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        return Result.success(postLikeService.getPostLikeWithPage(authUser.getUser().getId(), page, pageSize));
    }
}
