package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.entity.SysTag;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.service.UserTagRelationService;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标签控制器
 * 处理标签管理、搜索、用户关注等功能
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
     * 获取热门标签
     *
     * @param limit 返回数量（默认10）
     * @return 热门标签列表
     */
    @GetMapping("/hot")
    public Result<?> getHotTags(@RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {
        List<SysTag> hotTags = tagService.getHotTags(limit);
        return Result.success(hotTags);
    }

    /**
     * 搜索标签
     *
     * @param keyword 关键词
     * @return 匹配的标签列表
     */
    @GetMapping("/search")
    public Result<?> searchTags(@RequestParam("keyword") String keyword) {
        List<SysTag> tags = tagService.searchTags(keyword);
        return Result.success(tags);
    }

    /**
     * 切换关注标签状态
     *
     * @param tagId 标签ID
     * @param score 兴趣权重（可选，默认3.0）
     * @return 关注状态结果
     */
    @PostMapping("/{tagId}/toggle")
    public Result<?> toggleFollow(
            @PathVariable Long tagId,
            @RequestParam(value = "score", required = false, defaultValue = "3.0") BigDecimal score) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        boolean isFollowing = userTagRelationService.toggleFollow(userId, tagId, score);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("isFollowing", isFollowing);
        resultData.put("message", isFollowing ? "关注成功" : "取消关注成功");

        return Result.success(resultData);
    }

    /**
     * 关注标签
     *
     * @param tagId 标签ID
     * @param score 兴趣权重（可选，默认3.0）
     * @return 操作结果
     */
    @PostMapping("/{tagId}/follow")
    public Result<?> followTag(
            @PathVariable Long tagId,
            @RequestParam(value = "score", required = false, defaultValue = "3.0") BigDecimal score) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        boolean success = userTagRelationService.followTag(userId, tagId, score);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("success", success);
        resultData.put("message", success ? "关注成功" : "您已经关注过该标签");

        return Result.success(resultData);
    }

    /**
     * 取消关注标签
     *
     * @param tagId 标签ID
     * @return 操作结果
     */
    @DeleteMapping("/{tagId}/unfollow")
    public Result<?> unfollowTag(@PathVariable Long tagId) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        boolean success = userTagRelationService.unfollowTag(userId, tagId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("success", success);
        resultData.put("message", success ? "取消关注成功" : "您未关注过该标签");

        return Result.success(resultData);
    }

    /**
     * 检查当前用户是否关注了指定标签
     *
     * @param tagId 标签ID
     * @return 关注状态
     */
    @GetMapping("/{tagId}/status")
    public Result<?> checkFollowStatus(@PathVariable Long tagId) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        boolean isFollowing = userTagRelationService.isFollowing(userId, tagId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("isFollowing", isFollowing);

        return Result.success(resultData);
    }

    /**
     * 获取当前用户关注的标签
     *
     * @return 用户关注的标签列表
     */
    @GetMapping("/my-following")
    public Result<?> getMyFollowingTags() {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        List<SysTag> tags = userTagRelationService.getUserFollowingTags(userId);

        return Result.success(tags);
    }

    /**
     * 更新标签兴趣权重
     *
     * @param tagId 标签ID
     * @param score 新的权重分数（1.0-5.0）
     * @return 操作结果
     */
    @PutMapping("/{tagId}/score")
    public Result<?> updateTagScore(
            @PathVariable Long tagId,
            @RequestParam("score") BigDecimal score) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        boolean success = userTagRelationService.updateScore(userId, tagId, score);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("success", success);
        resultData.put("message", success ? "权重更新成功" : "更新失败，请检查是否已关注该标签");

        return Result.success(resultData);
    }
}
