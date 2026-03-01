package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.service.TrendStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trend-stat")
@RequiredArgsConstructor
public class TrendStatController {

    private final TrendStatService trendStatService;

    @GetMapping("/keyword-cloud")
    public Result<?> getKeywordCloud() {
        return Result.success(trendStatService.getKeywordCloud());
    }

    @GetMapping("/post-trend")
    public Result<?> getPostTrend() {
        return Result.success(trendStatService.getPostTrend());
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
                                 @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(trendStatService.getHeatRank(page, pageSize));
    }
}
