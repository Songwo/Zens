package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.entity.SysTag;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.CollaborativeFilteringService;
import com.campus.trend.campus_pulse.service.PostRecommendService;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 推荐控制器
 * 提供基于用户兴趣的帖子和标签推荐，以及协同过滤推荐
 */
@Slf4j
@RestController
@RequestMapping("/recommend")
public class RecommendController {

    private final PostRecommendService postRecommendService;
    private final CollaborativeFilteringService collaborativeFilteringService;

    @Autowired
    public RecommendController(PostRecommendService postRecommendService,
            CollaborativeFilteringService collaborativeFilteringService) {
        this.postRecommendService = postRecommendService;
        this.collaborativeFilteringService = collaborativeFilteringService;
    }

    /**
     * 获取推荐帖子（基于用户关注的标签）
     *
     * @param page     页码（默认1）
     * @param pageSize 每页大小（默认20）
     * @return 推荐的帖子列表
     */
    @GetMapping("/posts")
    public Result<?> getRecommendedPosts(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        IPage<SysPost> recommendedPosts = postRecommendService.recommendPosts(userId, page, pageSize);

        return Result.success(recommendedPosts);
    }

    /**
     * 获取推荐标签
     *
     * @param limit 返回数量（默认10）
     * @return 推荐的标签列表
     */
    @GetMapping("/tags")
    public Result<?> getRecommendedTags(
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        List<SysTag> recommendedTags = postRecommendService.recommendTags(userId, limit);

        return Result.success(recommendedTags);
    }

    /**
     * 获取相似帖子推荐（基于协同过滤）
     * "看了这篇帖子的人还看了..."
     *
     * @param postId 当前帖子ID
     * @param limit  返回数量（默认6）
     * @return 相似帖子列表
     */
    @GetMapping("/similar/{postId}")
    public Result<?> getSimilarPosts(
            @PathVariable String postId,
            @RequestParam(value = "limit", required = false, defaultValue = "6") int limit) {
        log.info("获取帖子 [{}] 的相似推荐，数量: {}", postId, limit);

        List<SysPost> similarPosts = collaborativeFilteringService.recommendByItemBased(postId, limit);

        return Result.success(similarPosts);
    }
}
