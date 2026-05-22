package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.CollectActionResp;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.PostCollectService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/post-collect")
public class PostCollectController {

    private final PostCollectService postCollectService;

    @PostMapping("/{postId}/toggle")
    public Result<CollectActionResp> toggleCollect(@PathVariable String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        boolean isCollected = postCollectService.toggleCollect(postId, authUser.getUser().getId());
        return Result.success(CollectActionResp.of(isCollected, isCollected ? "收藏成功" : "取消收藏成功"));
    }

    @GetMapping("/{postId}/status")
    public Result<CollectActionResp> checkCollectStatus(@PathVariable String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        boolean isCollected = postCollectService.isCollect(postId, authUser.getUser().getId());
        return Result.success(CollectActionResp.of(isCollected));
    }

    @GetMapping("/user")
    public Result<IPage<Post>> getUserCollectedPosts(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        return Result.success(postCollectService.getPostCollectWithPage(authUser.getUser().getId(), page, pageSize));
    }
}
