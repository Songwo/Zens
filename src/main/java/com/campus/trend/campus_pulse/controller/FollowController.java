package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.UserSimpleResp;
import com.campus.trend.campus_pulse.service.FollowService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Song：关注控制器
 * Song：说明
 */
@Slf4j
@RestController
@RequestMapping("/follow")
@Tag(name = "关注管理", description = "用户关注相关接口")
public class FollowController {

    @Autowired
    private FollowService followService;

    @PostMapping("/{userId}")
    @Operation(summary = "关注用户")
    public Result<Void> follow(@PathVariable String userId) {
        try {
            String currentUserId = SecurityUtils.getCurrentUserId();
            followService.follow(currentUserId, userId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        } catch (Exception e) {
            log.error("关注用户失败", e);
            return Result.failed("关注失败");
        }
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "取消关注")
    public Result<Void> unfollow(@PathVariable String userId) {
        try {
            String currentUserId = SecurityUtils.getCurrentUserId();
            followService.unfollow(currentUserId, userId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        } catch (Exception e) {
            log.error("取消关注失败", e);
            return Result.failed("取消关注失败");
        }
    }

    /**
     * Song：说明
     */
    @GetMapping("/is-following/{userId}")
    @Operation(summary = "检查是否已关注")
    public Result<Boolean> isFollowing(@PathVariable String userId) {
        try {
            String currentUserId = SecurityUtils.getCurrentUserId();
            boolean isFollowing = followService.isFollowing(currentUserId, userId);
            return Result.success(isFollowing);
        } catch (Exception e) {
            log.error("检查关注状态失败", e);
            return Result.failed("检查失败");
        }
    }

    /**
     * Song：说明
     */
    @GetMapping("/check/{userId}")
    @Operation(summary = "检查是否已关注（兼容旧路径）")
    public Result<Boolean> checkFollow(@PathVariable String userId) {
        return isFollowing(userId);
    }

    /**
     * Song：获取指定用户的关注列表（分页）
     * Song：说明
     */
    @GetMapping("/following/{userId}")
    @Operation(summary = "获取用户关注列表")
    public Result<?> getFollowingListByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            List<UserSimpleResp> list = followService.getFollowingList(userId);
            // Song：简单分页处理
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, list.size());
            List<UserSimpleResp> paged = start < list.size() ? list.subList(start, end) : List.of();

            Map<String, Object> result = new HashMap<>();
            result.put("records", paged);
            result.put("total", list.size());
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取关注列表失败", e);
            return Result.failed("获取失败");
        }
    }

    /**
     * Song：获取指定用户的粉丝列表（分页）
     * Song：说明
     */
    @GetMapping("/followers/{userId}")
    @Operation(summary = "获取用户粉丝列表")
    public Result<?> getFollowerListByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            List<UserSimpleResp> list = followService.getFollowerList(userId);
            // Song：简单分页处理
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, list.size());
            List<UserSimpleResp> paged = start < list.size() ? list.subList(start, end) : List.of();

            Map<String, Object> result = new HashMap<>();
            result.put("records", paged);
            result.put("total", list.size());
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取粉丝列表失败", e);
            return Result.failed("获取失败");
        }
    }

    /**
     * Song：获取当前用户的关注列表（兼容旧路径）
     */
    @GetMapping("/following")
    @Operation(summary = "获取我的关注列表")
    public Result<List<UserSimpleResp>> getFollowingList() {
        try {
            String currentUserId = SecurityUtils.getCurrentUserId();
            List<UserSimpleResp> list = followService.getFollowingList(currentUserId);
            return Result.success(list);
        } catch (Exception e) {
            log.error("获取关注列表失败", e);
            return Result.failed("获取失败");
        }
    }

    /**
     * Song：获取当前用户的粉丝列表（兼容旧路径）
     */
    @GetMapping("/followers")
    @Operation(summary = "获取我的粉丝列表")
    public Result<List<UserSimpleResp>> getFollowerList() {
        try {
            String currentUserId = SecurityUtils.getCurrentUserId();
            List<UserSimpleResp> list = followService.getFollowerList(currentUserId);
            return Result.success(list);
        } catch (Exception e) {
            log.error("获取粉丝列表失败", e);
            return Result.failed("获取失败");
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "获取关注统计")
    public Result<Map<String, Long>> getFollowStats() {
        try {
            String currentUserId = SecurityUtils.getCurrentUserId();
            Map<String, Long> stats = new HashMap<>();
            stats.put("followingCount", followService.getFollowingCount(currentUserId));
            stats.put("followerCount", followService.getFollowerCount(currentUserId));
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取关注统计失败", e);
            return Result.failed("获取失败");
        }
    }

    @GetMapping("/stats/{userId}")
    @Operation(summary = "获取指定用户的关注统计")
    public Result<Map<String, Long>> getUserFollowStats(@PathVariable String userId) {
        try {
            Map<String, Long> stats = new HashMap<>();
            stats.put("followingCount", followService.getFollowingCount(userId));
            stats.put("followerCount", followService.getFollowerCount(userId));
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取用户关注统计失败", e);
            return Result.failed("获取失败");
        }
    }
}
