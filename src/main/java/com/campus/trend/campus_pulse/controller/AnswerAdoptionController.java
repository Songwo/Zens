package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.AnswerAdoption;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.AnswerAdoptionService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 答案采纳控制器
 */
@RestController
@RequestMapping("/answer-adoption")
@Slf4j
public class AnswerAdoptionController {

    @Autowired
    private AnswerAdoptionService answerAdoptionService;

    /**
     * 采纳答案
     */
    @PostMapping("/adopt")
    public Result<?> adoptAnswer(@RequestParam String postId, @RequestParam String commentId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        answerAdoptionService.adoptAnswer(postId, commentId, authUser.getUser().getId());
        return Result.success("采纳成功");
    }

    /**
     * 取消采纳
     */
    @PostMapping("/cancel")
    public Result<?> cancelAdoption(@RequestParam String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        answerAdoptionService.cancelAdoption(postId, authUser.getUser().getId());
        return Result.success("已取消采纳");
    }

    /**
     * 获取帖子的采纳答案
     */
    @GetMapping("/{postId}")
    public Result<AnswerAdoption> getAdoptedAnswer(@PathVariable String postId) {
        AnswerAdoption adoption = answerAdoptionService.getAdoptedAnswer(postId);
        return Result.success(adoption);
    }
}
