package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.ReactionResp;
import com.campus.trend.campus_pulse.service.ReactionService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 内容表情反应控制器
 */
@RestController
@RequestMapping("/reaction")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    /** 切换/设置表情（需登录） */
    @PostMapping
    public Result<ReactionResp> react(@RequestParam String targetType,
                                      @RequestParam String targetId,
                                      @RequestParam String type) {
        String userId = SecurityUtils.getAuthenticatedUser().getUser().getId();
        return Result.success(reactionService.react(targetType, targetId, userId, type));
    }

    /** 查询单个目标的表情聚合（匿名可读，mine 为 null） */
    @GetMapping
    public Result<ReactionResp> get(@RequestParam String targetType,
                                    @RequestParam String targetId) {
        return Result.success(reactionService.getReactions(targetType, targetId, currentUserIdOrNull()));
    }

    /** 批量查询表情聚合（评论列表用），body 为目标ID数组 */
    @PostMapping("/batch")
    public Result<Map<String, ReactionResp>> batch(@RequestParam String targetType,
                                                   @RequestBody List<String> ids) {
        return Result.success(reactionService.getReactionsBatch(targetType, ids, currentUserIdOrNull()));
    }

    private String currentUserIdOrNull() {
        try {
            return SecurityUtils.getAuthenticatedUser().getUser().getId();
        } catch (Exception e) {
            return null;
        }
    }
}
