package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.service.UserTagRelationService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Song：标签控制器
 * Song：处理标签管理、搜索、用户关注等功能
 */
@Slf4j
@RestController
@RequestMapping("/tag")
public class TagController {

    private final TagService tagService;
    private final UserTagRelationService userTagRelationService;

    @Autowired
    public TagController(TagService tagService, UserTagRelationService userTagRelationService) {
        this.tagService = tagService;
        this.userTagRelationService = userTagRelationService;
    }

    /**
     * Song：获取热门标签
     *
     * Song：说明
     * Song：说明
     */
    @GetMapping("/hot")
    public Result<?> getHotTags(@RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {
        List<Tag> hotTags = tagService.getHotTags(limit);
        return Result.success(hotTags);
    }

    /**
     * Song：搜索标签
     *
     * Song：说明
     * Song：说明
     */
    @GetMapping("/search")
    public Result<?> searchTags(@RequestParam("keyword") String keyword) {
        List<Tag> tags = tagService.searchTags(keyword);
        return Result.success(tags);
    }

    /**
     * Song：切换关注标签状态
     *
     * Song：说明
     * Song：说明
     * Song：说明
     */
    @PostMapping("/{tagId}/toggle")
    public Result<?> toggleFollow(
            @PathVariable Long tagId,
            @RequestParam(value = "score", required = false, defaultValue = "3.0") BigDecimal score) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        boolean isFollowing = userTagRelationService.toggleFollow(userId, tagId, score);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("isFollowing", isFollowing);
        resultData.put("message", isFollowing ? "关注成功" : "取消关注成功");

        return Result.success(resultData);
    }

    /**
     * Song：关注标签
     *
     * Song：说明
     * Song：说明
     * Song：说明
     */
    @PostMapping("/{tagId}/follow")
    public Result<?> followTag(
            @PathVariable Long tagId,
            @RequestParam(value = "score", required = false, defaultValue = "3.0") BigDecimal score) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        boolean success = userTagRelationService.followTag(userId, tagId, score);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("success", success);
        resultData.put("message", success ? "关注成功" : "您已经关注过该标签");

        return Result.success(resultData);
    }

    /**
     * Song：取消关注标签
     *
     * Song：说明
     * Song：说明
     */
    @DeleteMapping("/{tagId}/unfollow")
    public Result<?> unfollowTag(@PathVariable Long tagId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        boolean success = userTagRelationService.unfollowTag(userId, tagId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("success", success);
        resultData.put("message", success ? "取消关注成功" : "您未关注过该标签");

        return Result.success(resultData);
    }

    /**
     * Song：检查当前用户是否关注了指定标签
     *
     * Song：说明
     * Song：说明
     */
    @GetMapping("/{tagId}/status")
    public Result<?> checkFollowStatus(@PathVariable Long tagId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        boolean isFollowing = userTagRelationService.isFollowing(userId, tagId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("isFollowing", isFollowing);

        return Result.success(resultData);
    }

    /**
     * Song：获取当前用户关注的标签
     *
     * Song：说明
     */
    @GetMapping("/my-following")
    public Result<?> getMyFollowingTags() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        List<Tag> tags = userTagRelationService.getUserFollowingTags(userId);

        return Result.success(tags);
    }

    /**
     * Song：更新标签兴趣权重
     *
     * Song：说明
     * Song：说明
     * Song：说明
     */
    @PutMapping("/{tagId}/score")
    public Result<?> updateTagScore(
            @PathVariable Long tagId,
            @RequestParam("score") BigDecimal score) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        boolean success = userTagRelationService.updateScore(userId, tagId, score);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("success", success);
        resultData.put("message", success ? "权重更新成功" : "更新失败，请检查是否已关注该标签");

        return Result.success(resultData);
    }
}
