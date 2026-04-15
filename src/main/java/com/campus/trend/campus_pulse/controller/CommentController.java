package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.CommentCreateReq;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.CommentService;
import com.campus.trend.campus_pulse.utils.ClientIpUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * Song：评论控制器
 * Song：提供评论的发布、删除、分页查询、点赞等接口
 */
@Slf4j
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private static final String COMMENT_RATE_LIMIT_PREFIX = "rate:comment:";
    private static final int RATE_WINDOW_SECONDS = 60;
    private static final int AUTH_RATE_LIMIT = 30;
    private static final int ANON_RATE_LIMIT = 8;

    private final CommentService commentService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Song：添加评论（需要登录）
     */
    @PostMapping("/add")
    public Result<?> addComment(@Valid @RequestBody CommentCreateReq request, HttpServletRequest httpRequest) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();
        if (!allowComment("uid:" + userId, AUTH_RATE_LIMIT)) {
            return Result.failed("评论过于频繁，请稍后再试");
        }
        commentService.addComment(request, userId);
        return Result.success();
    }

    /**
     * Song：创建评论（支持匿名，未登录用户也可以发评论）
     */
    @PostMapping("/create")
    public Result<?> createComment(@Valid @RequestBody CommentCreateReq request, HttpServletRequest httpRequest) {
        // Song：通过 SecurityContext 判断是否已认证，避免用 try-catch 做流控
        String userId = SecurityUtils.getCurrentUserId();
        if (org.springframework.util.StringUtils.hasText(userId)) {
            // Song：已登录用户
            if (!allowComment("uid:" + userId, AUTH_RATE_LIMIT)) {
                return Result.failed("评论过于频繁，请稍后再试");
            }
            commentService.addComment(request, userId);
            log.info("用户 {} 发表评论，匿名: {}", userId, request.getIsAnonymous());
            return Result.success();
        }

        // Song：未登录用户，按 IP 限流并强制匿名
        String ip = resolveClientIp(httpRequest);
        if (!allowComment("ip:" + ip, ANON_RATE_LIMIT)) {
            return Result.failed("评论过于频繁，请稍后再试");
        }
        request.setIsAnonymous(1);
        commentService.addComment(request, null);
        log.info("匿名用户发表评论");
        return Result.success();
    }

    /* Song：删除评论（仅评论作者或管理员） */
    @DeleteMapping("/{id}")
    public Result<?> deleteComment(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        commentService.deleteComment(id, authUser.getUser().getId());
        return Result.success();
    }

    /* Song：分页获取某帖子的评论列表 */
    @GetMapping("/post/{postId}")
    public Result<?> getCommentsByPostId(@PathVariable String postId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(commentService.getCommentsByPostId(postId, page, size));
    }

    /* Song：点赞/取消点赞评论 */
    @PostMapping("/{id}/like")
    public Result<?> likeComment(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        commentService.toggleLike(id, authUser.getUser().getId());
        return Result.success();
    }

    private boolean allowComment(String dimension, int limit) {
        String key = COMMENT_RATE_LIMIT_PREFIX + dimension;
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current == null) {
            return true;
        }
        if (current == 1L) {
            stringRedisTemplate.expire(key, RATE_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        return current <= limit;
    }

    private String resolveClientIp(HttpServletRequest request) {
        return ClientIpUtils.resolveClientIp(request);
    }
}
