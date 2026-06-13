package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.PollVoteReq;
import com.campus.trend.campus_pulse.dto.response.PollResp;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.PollService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 帖子投票控制器。
 * 投票数据独立于帖子详情缓存，票数变动实时反映。
 */
@RestController
@RequestMapping("/poll")
@Slf4j
public class PollController {

    @Autowired
    private PollService pollService;

    /**
     * 按帖子ID查询投票（访客可访问，返回当前用户视角的可投/已投/结果状态）。
     * 帖子无投票时 data 为 null。
     */
    @GetMapping("/by-post/{postId}")
    public Result<PollResp> getByPost(@PathVariable String postId) {
        String userId = SecurityUtils.getCurrentUserId();
        return Result.success(pollService.getByPostId(postId, userId));
    }

    /**
     * 投票（需登录）。
     */
    @PostMapping("/vote")
    public Result<PollResp> vote(@Valid @RequestBody PollVoteReq req) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        return Result.success(pollService.vote(req, authUser.getUser().getId()));
    }

    /**
     * 提前关闭投票（作者 / 版主 / 管理员）。
     */
    @PostMapping("/{pollId}/close")
    public Result<PollResp> close(@PathVariable Long pollId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        return Result.success(pollService.close(pollId, authUser.getUser().getId()));
    }
}
