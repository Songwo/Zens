package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.CodeRiskAnalyzeReq;
import com.campus.trend.campus_pulse.service.TrendStatService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trend-stat")
@RequiredArgsConstructor
public class TrendStatController {

    private final TrendStatService trendStatService;

    @GetMapping("/dashboard")
    public Result<?> getCommunityDashboard() {
        return Result.success(trendStatService.getCommunityDashboard());
    }

    @GetMapping("/keyword-cloud")
    public Result<?> getKeywordCloud() {
        return Result.success(trendStatService.getKeywordCloud());
    }

    @GetMapping("/post-trend")
    public Result<?> getPostTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.success(trendStatService.getPostTrend(days));
    }

    @GetMapping("/user-trend")
    public Result<?> getUserTrend() {
        return Result.success(trendStatService.getUserTrend());
    }

    @GetMapping("/prediction")
    public Result<?> getTrendPrediction() {
        return Result.success(trendStatService.getTrendPrediction());
    }

    @GetMapping("/section-pie")
    public Result<?> getSectionPie() {
        return Result.success(trendStatService.getSectionPie());
    }

    @GetMapping("/heat-rank")
    public Result<?> getHeatRank(@RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "10") int pageSize,
                                 @RequestParam(required = false) String timeRange) {
        return Result.success(trendStatService.getHeatRank(page, pageSize, timeRange));
    }

    @GetMapping("/tag-relations")
    public Result<?> getTagRelations(@RequestParam String keyword) {
        return Result.success(trendStatService.getTagRelations(keyword));
    }

    @GetMapping("/user-insight")
    public Result<?> getUserInsight() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        return Result.success(trendStatService.getUserInsight(userId));
    }

    @PostMapping("/code-risk")
    public Result<?> analyzeCodeRisk(@Valid @RequestBody CodeRiskAnalyzeReq req) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        return Result.success(trendStatService.analyzeCodeSnippet(req.getCode(), req.getLanguage()));
    }
}
