package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.annotation.RateLimit;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.request.SupporterFeedbackCreateReq;
import com.campus.trend.campus_pulse.dto.request.SupporterFeedbackReplyReq;
import com.campus.trend.campus_pulse.dto.response.SimplePageResp;
import com.campus.trend.campus_pulse.dto.response.SupporterFeedbackResp;
import com.campus.trend.campus_pulse.service.SupporterFeedbackService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SupporterFeedbackController {
    private final SupporterFeedbackService feedbackService;

    @PostMapping("/supporter/feedback")
    @RateLimit(key = "supporter_feedback_create", limit = 3, windowSeconds = 3600)
    public ResponseEntity<Result<SupporterFeedbackResp>> create(@Valid @RequestBody SupporterFeedbackCreateReq request) {
        try {
            return ResponseEntity.ok(Result.success(
                    feedbackService.create(SecurityUtils.getCurrentUserId(), request)));
        } catch (BusinessException error) {
            return supporterAccessError(error);
        }
    }

    @GetMapping("/supporter/feedback")
    @RateLimit(key = "supporter_feedback_mine", limit = 60, windowSeconds = 60)
    public ResponseEntity<Result<SimplePageResp<SupporterFeedbackResp>>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            return ResponseEntity.ok(Result.success(
                    feedbackService.pageMine(SecurityUtils.getCurrentUserId(), page, pageSize)));
        } catch (BusinessException error) {
            return supporterAccessError(error);
        }
    }

    @GetMapping({"/admin/supporter-feedback", "/api/admin/supporter-feedback"})
    public Result<SimplePageResp<SupporterFeedbackResp>> admin(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userId) {
        if (!PermissionUtils.isAdmin()) return Result.error(ResultCode.NO_PERMISSION, "仅管理员可查看共建反馈");
        return Result.success(feedbackService.pageAdmin(page, pageSize, status, userId));
    }

    @PatchMapping({"/admin/supporter-feedback/{id}/reply", "/api/admin/supporter-feedback/{id}/reply"})
    @RateLimit(key = "supporter_feedback_reply", limit = 30, windowSeconds = 60)
    public Result<SupporterFeedbackResp> reply(@PathVariable Long id,
                                               @Valid @RequestBody SupporterFeedbackReplyReq request) {
        if (!PermissionUtils.isAdmin()) return Result.error(ResultCode.NO_PERMISSION, "仅管理员可回复共建反馈");
        return Result.success(feedbackService.reply(id, SecurityUtils.getCurrentUserId(), request));
    }

    private <T> ResponseEntity<Result<T>> supporterAccessError(BusinessException error) {
        if (error.getResultCode() != ResultCode.NO_PERMISSION) throw error;
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(ResultCode.NO_PERMISSION, error.getMessage()));
    }
}
