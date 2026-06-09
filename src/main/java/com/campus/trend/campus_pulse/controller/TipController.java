package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.TipReq;
import com.campus.trend.campus_pulse.entity.TipRecord;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.TipService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 打赏控制器
 */
@RestController
@RequestMapping("/tip")
@Slf4j
public class TipController {

    @Autowired
    private TipService tipService;

    /**
     * 打赏
     */
    @PostMapping("/send")
    public Result<?> tip(@Valid @RequestBody TipReq req) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        tipService.tip(req.getTargetType(), req.getTargetId(), req.getAmount(), req.getMessage(), authUser.getUser().getId());
        return Result.success("打赏成功");
    }

    /**
     * 获取我收到的打赏
     */
    @GetMapping("/received")
    public Result<List<TipRecord>> getReceivedTips() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        List<TipRecord> tips = tipService.getReceivedTips(authUser.getUser().getId());
        return Result.success(tips);
    }

    /**
     * 获取我发出的打赏
     */
    @GetMapping("/sent")
    public Result<List<TipRecord>> getSentTips() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        List<TipRecord> tips = tipService.getSentTips(authUser.getUser().getId());
        return Result.success(tips);
    }

    /**
     * 获取目标的打赏统计
     */
    @GetMapping("/sum")
    public Result<Map<String, Object>> getTipSum(@RequestParam String targetType, @RequestParam String targetId) {
        Integer sum = tipService.getTipSum(targetType, targetId);
        Map<String, Object> result = new HashMap<>();
        result.put("totalAmount", sum);
        return Result.success(result);
    }
}
