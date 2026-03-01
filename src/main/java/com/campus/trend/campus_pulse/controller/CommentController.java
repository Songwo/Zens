package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.CommentCreateReq;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.CommentService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Song：评论控制器
 * Song：提供评论的发布、删除、分页查询、点赞等接口
 */
@Slf4j
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * Song：添加评论（需要登录）
     */
    @PostMapping("/add")
    public Result<?> addComment(@Valid @RequestBody CommentCreateReq request) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        commentService.addComment(request, authUser.getUser().getId());
        return Result.success();
    }

    /**
     * Song：创建评论（支持匿名，未登录用户也可以发评论）
     */
    @PostMapping("/create")
    public Result<?> createComment(@Valid @RequestBody CommentCreateReq request) {
        try {
            // Song：尝试获取当前用户
            AuthUser authUser = SecurityUtils.getAuthenticatedUser();
            if (authUser != null && authUser.getUser() != null) {
                // Song：已登录用户
                String userId = authUser.getUser().getId();
                commentService.addComment(request, userId);
                log.info("用户 {} 发表评论，匿名: {}", userId, request.getIsAnonymous());
                return Result.success();
            }
        } catch (Exception e) {
            // Song：未登录用户，继续执行下面的逻辑
            log.debug("未登录用户发表匿名评论");
        }

        // Song：说明
        request.setIsAnonymous(1); // Song：强制匿名
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
}
