package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.service.PublicDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

/**
 * Song：热度排行控制器 - 提供实时热度排行数据
 */
@Slf4j
@RestController
@RequestMapping("/heat-rank")
@RequiredArgsConstructor
public class HeatRankController {

    private final PublicDataService publicDataService;

    @GetMapping("/top")
    public Result<?> getTopHeatRank(@RequestParam(required = false) String timeRange,
                                    @RequestParam(defaultValue = "10") int limit) {
        try {
            return Result.success(publicDataService.getHotRank(limit, timeRange));
        } catch (Exception e) {
            log.error("获取热度排行失败", e);
            return Result.success(Collections.emptyList());
        }
    }
}
