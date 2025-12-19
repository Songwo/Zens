package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.dto.request.CreateCommentRequest;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.CommentService;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/sys-comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 添加评论（需要登录）
     */
    @PostMapping("/add")
    public Result<?> addComment(@Valid @RequestBody CreateCommentRequest request) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        commentService.addComment(request, authSysUser.getSysUser().getId());
        return Result.success();
    }

    /**
     * 创建评论（支持匿名，未登录用户也可以发评论）
     */
    @PostMapping("/create")
    public Result<?> createComment(@Valid @RequestBody CreateCommentRequest request) {
        try {
            // 尝试获取当前用户
            AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
            if (authSysUser != null && authSysUser.getSysUser() != null) {
                // 已登录用户
                String userId = authSysUser.getSysUser().getId();
                commentService.addComment(request, userId);
                log.info("用户 {} 发表评论，匿名: {}", userId, request.getIsAnonymous());
                return Result.success();
            }
        } catch (Exception e) {
            // 未登录用户，继续执行下面的逻辑
            log.debug("未登录用户发表匿名评论");
        }

        // 匿名用户：使用 null 作为 userId，并强制设置为匿名
        request.setIsAnonymous(1); // 强制匿名
        commentService.addComment(request, null);
        log.info("匿名用户发表评论");
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteComment(@PathVariable String id) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        commentService.deleteComment(id, authSysUser.getSysUser().getId());
        return Result.success();
    }

    @GetMapping("/post/{postId}")
    public Result<?> getCommentsByPostId(@PathVariable String postId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(commentService.getCommentsByPostId(postId, page, size));
    }

    @PostMapping("/{id}/like")
    public Result<?> likeComment(@PathVariable String id) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        commentService.toggleLike(id, authSysUser.getSysUser().getId());
        return Result.success();
    }
}
