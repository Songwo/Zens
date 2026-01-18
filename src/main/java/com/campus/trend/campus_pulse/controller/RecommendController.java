package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.dto.response.RecommendPostResponse;
import com.campus.trend.campus_pulse.service.PostRecommendService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "推荐管理", description = "内容推荐相关接口")
@RestController
@RequestMapping("/recommend")
public class RecommendController {

    private final PostRecommendService postRecommendService;

    @Autowired
    public RecommendController(PostRecommendService postRecommendService) {
        this.postRecommendService = postRecommendService;
    }

    @Operation(summary = "获取混合推荐列表", description = "基于用户兴趣、行为、协同过滤及热门程度的综合推荐")
    @GetMapping("/list")
    public Result<IPage<RecommendPostResponse>> getRecommendList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        String userId = SecurityUtils.getCurrentUserId();
        IPage<RecommendPostResponse> recommendations = postRecommendService.getHybridRecommendations(userId, page, pageSize);
        return Result.success(recommendations);
    }

    @Operation(summary = "获取帖子详情页底部推荐", description = "同专业发布、相同分类标签、热度兜底")
    @GetMapping("/post-detail/{postId}")
    public Result<List<RecommendPostResponse>> getPostDetailRecommend(
            @PathVariable String postId,
            @RequestParam(defaultValue = "6") int limit) {
        String userId = SecurityUtils.getCurrentUserId();
        return Result.success(postRecommendService.getPostDetailRecommendations(postId, userId, limit));
    }
}
