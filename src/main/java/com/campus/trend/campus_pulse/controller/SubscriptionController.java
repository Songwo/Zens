package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.PostSubscription;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.PostSubscriptionService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 帖子订阅控制器（主题追踪）。
 * 订阅关系驱动两类触达：即时站内通知（实时）+ 每日邮件摘要（PostDigestTask）。
 */
@RestController
@RequestMapping("/subscription")
@Slf4j
@RequiredArgsConstructor
public class SubscriptionController {

    private final PostSubscriptionService postSubscriptionService;

    /**
     * 手动订阅（追踪）帖子，幂等。
     */
    @PostMapping("/{postId}")
    public Result<?> subscribe(@PathVariable String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postSubscriptionService.subscribe(authUser.getUser().getId(), postId, "manual");
        return Result.success();
    }

    /**
     * 取消订阅。
     */
    @DeleteMapping("/{postId}")
    public Result<?> unsubscribe(@PathVariable String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postSubscriptionService.unsubscribe(authUser.getUser().getId(), postId);
        return Result.success();
    }

    /**
     * 查询当前用户是否订阅了该帖（未登录返回 false，不报错，便于详情页直接调用）。
     */
    @GetMapping("/{postId}/status")
    public Result<Map<String, Boolean>> status(@PathVariable String postId) {
        String userId = SecurityUtils.getCurrentUserId();
        boolean subscribed = userId != null && postSubscriptionService.isSubscribed(userId, postId);
        return Result.success(Map.of("subscribed", subscribed));
    }

    /**
     * 我追踪的帖子列表（分页，按订阅时间倒序）。
     */
    @GetMapping("/my")
    public Result<Page<PostSubscription>> my(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        return Result.success(postSubscriptionService.listByUser(authUser.getUser().getId(), page, size));
    }
}
