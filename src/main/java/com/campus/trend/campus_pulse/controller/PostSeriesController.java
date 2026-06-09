package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.CreateSeriesReq;
import com.campus.trend.campus_pulse.entity.PostSeries;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.PostSeriesService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 帖子系列控制器
 */
@RestController
@RequestMapping("/post-series")
@Slf4j
public class PostSeriesController {

    @Autowired
    private PostSeriesService postSeriesService;

    /**
     * 创建系列
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> createSeries(@Valid @RequestBody CreateSeriesReq req) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        Long seriesId = postSeriesService.createSeries(req.getTitle(), req.getDescription(), authUser.getUser().getId());

        Map<String, Object> result = new HashMap<>();
        result.put("seriesId", seriesId);
        return Result.success(result);
    }

    /**
     * 添加帖子到系列
     */
    @PostMapping("/{seriesId}/add-post")
    public Result<?> addPost(@PathVariable Long seriesId, @RequestParam String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postSeriesService.addPostToSeries(seriesId, postId, authUser.getUser().getId());
        return Result.success("添加成功");
    }

    /**
     * 从系列移除帖子
     */
    @PostMapping("/{seriesId}/remove-post")
    public Result<?> removePost(@PathVariable Long seriesId, @RequestParam String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postSeriesService.removePostFromSeries(seriesId, postId, authUser.getUser().getId());
        return Result.success("移除成功");
    }

    /**
     * 获取系列下的所有帖子
     */
    @GetMapping("/{seriesId}/posts")
    public Result<List<Object>> getSeriesPosts(@PathVariable Long seriesId) {
        List<Object> posts = postSeriesService.getSeriesPosts(seriesId);
        return Result.success(posts);
    }

    /**
     * 获取用户的系列列表
     */
    @GetMapping("/my")
    public Result<List<PostSeries>> getMySeries() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        List<PostSeries> series = postSeriesService.getUserSeries(authUser.getUser().getId());
        return Result.success(series);
    }

    /**
     * 获取指定用户的系列列表
     */
    @GetMapping("/user/{userId}")
    public Result<List<PostSeries>> getUserSeries(@PathVariable String userId) {
        List<PostSeries> series = postSeriesService.getUserSeries(userId);
        return Result.success(series);
    }

    /**
     * 更新系列中帖子的排序
     */
    @PostMapping("/{seriesId}/reorder")
    public Result<?> updateOrder(@PathVariable Long seriesId,
                                  @RequestParam String postId,
                                  @RequestParam Integer orderIndex) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postSeriesService.updateSeriesOrder(seriesId, postId, orderIndex, authUser.getUser().getId());
        return Result.success("排序更新成功");
    }

    /**
     * 获取系列详情
     */
    @GetMapping("/{seriesId}")
    public Result<PostSeries> getSeriesDetail(@PathVariable Long seriesId) {
        PostSeries series = postSeriesService.getById(seriesId);
        return Result.success(series);
    }
}
