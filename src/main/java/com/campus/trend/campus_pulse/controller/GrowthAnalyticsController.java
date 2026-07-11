package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.annotation.RateLimit;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.GrowthEventReq;
import com.campus.trend.campus_pulse.service.GrowthAnalyticsService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class GrowthAnalyticsController {
    private final GrowthAnalyticsService service;

    @PostMapping("/growth/events")
    @RateLimit(key = "growth_event", limit = 120, windowSeconds = 60, limitType = RateLimit.LimitType.IP)
    public Result<?> record(@Valid @RequestBody GrowthEventReq request) {
        service.record(request, SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @GetMapping("/admin/growth/dashboard")
    public Result<?> dashboard(@RequestParam(defaultValue = "30") int days) {
        return Result.success(service.dashboard(days));
    }
}
