package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.SiteStatsResp;
import com.campus.trend.campus_pulse.service.PublicDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Song：站点统计接口
 */
@Slf4j
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final PublicDataService publicDataService;

    /**
     * Song：站点汇总统计
     * Song：说明
     */
    @GetMapping("/site")
    public Result<SiteStatsResp> getSiteStats() {
        try {
            return Result.success(publicDataService.getSiteStats());
        } catch (Exception e) {
            log.error("获取站点统计失败", e);
            return Result.success(new SiteStatsResp());
        }
    }
}
