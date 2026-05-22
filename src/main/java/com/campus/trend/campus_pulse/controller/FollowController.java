package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.FollowStatsResp;
import com.campus.trend.campus_pulse.dto.response.SimplePageResp;
import com.campus.trend.campus_pulse.dto.response.UserSimpleResp;
import com.campus.trend.campus_pulse.service.FollowService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/follow")
@Tag(name = "关注管理", description = "用户关注相关接口")
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{userId}")
    @Operation(summary = "关注用户")
    public Result<Void> follow(@PathVariable String userId) {
        followService.follow(SecurityUtils.getCurrentUserId(), userId);
        return Result.success();
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "取消关注")
    public Result<Void> unfollow(@PathVariable String userId) {
        followService.unfollow(SecurityUtils.getCurrentUserId(), userId);
        return Result.success();
    }

    @GetMapping("/is-following/{userId}")
    @Operation(summary = "检查是否已关注")
    public Result<Boolean> isFollowing(@PathVariable String userId) {
        return Result.success(followService.isFollowing(SecurityUtils.getCurrentUserId(), userId));
    }

    @GetMapping("/check/{userId}")
    @Operation(summary = "检查是否已关注（兼容旧路径）")
    public Result<Boolean> checkFollow(@PathVariable String userId) {
        return isFollowing(userId);
    }

    @GetMapping("/following/{userId}")
    @Operation(summary = "获取用户关注列表")
    public Result<SimplePageResp<UserSimpleResp>> getFollowingListByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(slice(followService.getFollowingList(userId), page, pageSize));
    }

    @GetMapping("/followers/{userId}")
    @Operation(summary = "获取用户粉丝列表")
    public Result<SimplePageResp<UserSimpleResp>> getFollowerListByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(slice(followService.getFollowerList(userId), page, pageSize));
    }

    @GetMapping("/following")
    @Operation(summary = "获取我的关注列表")
    public Result<List<UserSimpleResp>> getFollowingList() {
        return Result.success(followService.getFollowingList(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/followers")
    @Operation(summary = "获取我的粉丝列表")
    public Result<List<UserSimpleResp>> getFollowerList() {
        return Result.success(followService.getFollowerList(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/stats")
    @Operation(summary = "获取关注统计")
    public Result<FollowStatsResp> getFollowStats() {
        return Result.success(buildStats(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/stats/{userId}")
    @Operation(summary = "获取指定用户的关注统计")
    public Result<FollowStatsResp> getUserFollowStats(@PathVariable String userId) {
        return Result.success(buildStats(userId));
    }

    private FollowStatsResp buildStats(String userId) {
        return new FollowStatsResp(
                followService.getFollowingCount(userId),
                followService.getFollowerCount(userId));
    }

    private static <T> SimplePageResp<T> slice(List<T> list, int page, int pageSize) {
        if (list == null || list.isEmpty()) {
            return SimplePageResp.of(List.of(), 0);
        }
        int start = Math.max(0, (page - 1) * pageSize);
        if (start >= list.size()) {
            return SimplePageResp.of(List.of(), list.size());
        }
        int end = Math.min(start + pageSize, list.size());
        return SimplePageResp.of(list.subList(start, end), list.size());
    }
}
