package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.TagFollowActionResp;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.service.UserTagRelationService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/tag")
public class TagController {

    private final TagService tagService;
    private final UserTagRelationService userTagRelationService;

    @GetMapping("/hot")
    public Result<List<Tag>> getHotTags(@RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {
        return Result.success(tagService.getHotTags(limit));
    }

    @GetMapping("/search")
    public Result<List<Tag>> searchTags(@RequestParam("keyword") String keyword) {
        return Result.success(tagService.searchTags(keyword));
    }

    @PostMapping("/{tagId}/toggle")
    public Result<TagFollowActionResp> toggleFollow(
            @PathVariable Long tagId,
            @RequestParam(value = "score", required = false, defaultValue = "3.0") BigDecimal score) {
        String userId = currentUserId();
        boolean isFollowing = userTagRelationService.toggleFollow(userId, tagId, score);
        return Result.success(TagFollowActionResp.following(isFollowing, isFollowing ? "关注成功" : "取消关注成功"));
    }

    @PostMapping("/{tagId}/follow")
    public Result<TagFollowActionResp> followTag(
            @PathVariable Long tagId,
            @RequestParam(value = "score", required = false, defaultValue = "3.0") BigDecimal score) {
        boolean success = userTagRelationService.followTag(currentUserId(), tagId, score);
        return Result.success(TagFollowActionResp.success(success, success ? "关注成功" : "您已经关注过该标签"));
    }

    @DeleteMapping("/{tagId}/unfollow")
    public Result<TagFollowActionResp> unfollowTag(@PathVariable Long tagId) {
        boolean success = userTagRelationService.unfollowTag(currentUserId(), tagId);
        return Result.success(TagFollowActionResp.success(success, success ? "取消关注成功" : "您未关注过该标签"));
    }

    @GetMapping("/{tagId}/status")
    public Result<TagFollowActionResp> checkFollowStatus(@PathVariable Long tagId) {
        boolean isFollowing = userTagRelationService.isFollowing(currentUserId(), tagId);
        return Result.success(TagFollowActionResp.followingStatus(isFollowing));
    }

    @GetMapping("/my-following")
    public Result<List<Tag>> getMyFollowingTags() {
        return Result.success(userTagRelationService.getUserFollowingTags(currentUserId()));
    }

    @PutMapping("/{tagId}/score")
    public Result<TagFollowActionResp> updateTagScore(
            @PathVariable Long tagId,
            @RequestParam("score") BigDecimal score) {
        boolean success = userTagRelationService.updateScore(currentUserId(), tagId, score);
        return Result.success(TagFollowActionResp.success(success, success ? "权重更新成功" : "更新失败，请检查是否已关注该标签"));
    }

    private static String currentUserId() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        return authUser.getUser().getId();
    }
}
