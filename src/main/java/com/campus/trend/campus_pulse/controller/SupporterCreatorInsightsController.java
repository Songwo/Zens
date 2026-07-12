package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.annotation.RateLimit;
import com.campus.trend.campus_pulse.dto.response.SupporterCreatorInsightsResp;
import com.campus.trend.campus_pulse.service.SupporterCreatorInsightsService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SupporterCreatorInsightsController {
    private final SupporterCreatorInsightsService service;

    @GetMapping("/supporter/creator-insights")
    @RateLimit(key = "supporter_creator_insights", limit = 30, windowSeconds = 60)
    public Result<SupporterCreatorInsightsResp> insights(
            @RequestParam(defaultValue = "30") int days) {
        return Result.success(service.getInsights(SecurityUtils.getCurrentUserId(), days));
    }
}
