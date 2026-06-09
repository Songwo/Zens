package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.CheckInStatusResp;
import com.campus.trend.campus_pulse.service.CheckInService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日签到控制器
 */
@RestController
@RequestMapping("/check-in")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    /** 查询当前用户签到状态 */
    @GetMapping("/status")
    public Result<CheckInStatusResp> status() {
        String userId = SecurityUtils.getAuthenticatedUser().getUser().getId();
        return Result.success(checkInService.getStatus(userId));
    }

    /** 执行签到 */
    @PostMapping
    public Result<CheckInStatusResp> checkIn() {
        String userId = SecurityUtils.getAuthenticatedUser().getUser().getId();
        return Result.success(checkInService.checkIn(userId));
    }
}
