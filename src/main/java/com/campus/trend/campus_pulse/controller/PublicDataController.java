package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.PublicHomeBootstrapResp;
import com.campus.trend.campus_pulse.service.PublicDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicDataController {

    private final PublicDataService publicDataService;

    @GetMapping("/home-bootstrap")
    public Result<PublicHomeBootstrapResp> getHomeBootstrap(
            @RequestParam(defaultValue = "10") int hotTagLimit,
            @RequestParam(defaultValue = "5") int hotRankLimit,
            @RequestParam(defaultValue = "WEEK") String timeRange) {
        return Result.success(publicDataService.getHomeBootstrap(hotTagLimit, hotRankLimit, timeRange));
    }
}
