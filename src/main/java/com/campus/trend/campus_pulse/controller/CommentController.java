package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.dto.request.CreateCommentRequest;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.CommentService;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sys-comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/add")
    public Result<?> addComment(@Valid @RequestBody CreateCommentRequest request) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        commentService.addComment(request, authSysUser.getSysUser().getId());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteComment(@PathVariable String id) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        commentService.deleteComment(id, authSysUser.getSysUser().getId());
        return Result.success();
    }

    @GetMapping("/post/{postId}")
    public Result<?> getCommentsByPostId(@PathVariable String postId) {
        return Result.success(commentService.getCommentsByPostId(postId));
    }
}
