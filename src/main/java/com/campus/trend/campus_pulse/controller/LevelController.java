package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.LevelInfoResp;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/level")
@RequiredArgsConstructor
public class LevelController {

    private final LevelService levelService;

    private static final int[] LEVEL_THRESHOLDS = {0, 100, 300, 600, 1000, 1500, 2100, 2800, 3600, 4500};

    @GetMapping("/info")
    public Result<LevelInfoResp> getLevelInfo() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();
        LevelInfoResp info = levelService.getUserLevelInfo(userId);
        return Result.success(info);
    }

    @GetMapping("/thresholds")
    public Result<List<Map<String, Object>>> getThresholds() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < LEVEL_THRESHOLDS.length; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("level", i + 1);
            item.put("experience", LEVEL_THRESHOLDS[i]);
            list.add(item);
        }
        return Result.success(list);
    }

    /**
     * Song：经验记录
     * Song：说明
     */
    @GetMapping("/exp-records")
    public Result<Map<String, Object>> getExpRecords(@RequestParam(defaultValue = "7") int days,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int pageSize) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        Integer daysFilter = days > 0 ? days : null;
        return Result.success(levelService.getExperienceRecords(userId, daysFilter, page, pageSize));
    }
}
