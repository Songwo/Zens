package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.WebVitalMetricReq;
import com.campus.trend.campus_pulse.monitor.WebVitalRecorder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "前端体验指标", description = "真实用户 Web Vitals 指标采集")
@RestController
@RequestMapping({"/performance/web-vitals", "/api/performance/web-vitals"})
@RequiredArgsConstructor
public class WebVitalsController {

    private final WebVitalRecorder webVitalRecorder;

    @Operation(summary = "上报 Web Vitals 指标")
    @PostMapping
    public Result<Void> report(@RequestBody WebVitalMetricReq request, HttpServletRequest servletRequest) {
        if (request != null && (request.getUserAgent() == null || request.getUserAgent().isBlank())) {
            request.setUserAgent(servletRequest.getHeader("User-Agent"));
        }
        webVitalRecorder.record(request);
        return Result.success();
    }

    @Operation(summary = "获取 Web Vitals 汇总")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(webVitalRecorder.summary());
    }

    @Operation(summary = "查询 Web Vitals 明细")
    @GetMapping("/events")
    public Result<Object> events(@RequestParam(required = false) String metric,
                                 @RequestParam(required = false) String route,
                                 @RequestParam(required = false) Integer limit) {
        return Result.success(webVitalRecorder.list(metric, route, limit));
    }
}
