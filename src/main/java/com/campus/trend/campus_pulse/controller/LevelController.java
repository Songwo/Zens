package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.LevelExpRecordPageResp;
import com.campus.trend.campus_pulse.dto.response.LevelInfoResp;
import com.campus.trend.campus_pulse.dto.response.LevelThresholdResp;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/level")
@RequiredArgsConstructor
public class LevelController {

    private final LevelService levelService;

    private static final int[] LEVEL_THRESHOLDS = {0, 100, 300, 600, 1000, 1500, 2100, 2800, 3600, 4500};

    @GetMapping("/info")
    public Result<LevelInfoResp> getLevelInfo() {
        return Result.success(levelService.getUserLevelInfo(SecurityUtils.getAuthenticatedUser().getUser().getId()));
    }

    @GetMapping("/thresholds")
    public Result<List<LevelThresholdResp>> getThresholds() {
        List<LevelThresholdResp> list = new ArrayList<>(LEVEL_THRESHOLDS.length);
        for (int i = 0; i < LEVEL_THRESHOLDS.length; i++) {
            list.add(new LevelThresholdResp(i + 1, LEVEL_THRESHOLDS[i]));
        }
        return Result.success(list);
    }

    @GetMapping("/exp-records")
    public Result<LevelExpRecordPageResp> getExpRecords(@RequestParam(defaultValue = "7") int days,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int pageSize) {
        String userId = SecurityUtils.getAuthenticatedUser().getUser().getId();
        Integer daysFilter = days > 0 ? days : null;
        return Result.success(levelService.getExperienceRecords(userId, daysFilter, page, pageSize));
    }
}
