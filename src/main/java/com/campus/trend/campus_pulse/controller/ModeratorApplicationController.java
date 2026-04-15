package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.request.ModeratorApplyReq;
import com.campus.trend.campus_pulse.dto.request.ModeratorReviewReq;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.ModeratorApplicationService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/moderator")
public class ModeratorApplicationController {

    private final ModeratorApplicationService moderatorApplicationService;

    public ModeratorApplicationController(ModeratorApplicationService moderatorApplicationService) {
        this.moderatorApplicationService = moderatorApplicationService;
    }

    /**
     * Song：用户申请版主
     * Song：说明
     */
    @PostMapping("/apply")
    public Result<?> apply(@Valid @RequestBody ModeratorApplyReq body) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();
        moderatorApplicationService.apply(userId, body.getSectionId(), body.getReason());
        return Result.success("申请已提交，请等待审核");
    }

    /**
     * Song：获取当前用户的申请列表
     * Song：说明
     */
    @GetMapping("/my-applications")
    public Result<?> myApplications() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        return Result.success(moderatorApplicationService.getMyApplications(authUser.getUser().getId()));
    }

    /**
     * Song：管理员获取所有待审核申请
     * Song：说明
     */
    @GetMapping("/applications")
    public Result<?> applications() {
        ensureAdmin();
        return Result.success(moderatorApplicationService.getAllApplications());
    }

    /**
     * Song：管理员批准申请
     * Song：说明
     */
    @PostMapping("/approve/{id}")
    public Result<?> approve(@PathVariable Long id, @Valid @RequestBody(required = false) ModeratorReviewReq body) {
        ensureAdmin();
        String reviewNote = body != null ? body.getReviewNote() : null;
        moderatorApplicationService.approve(id, reviewNote);
        return Result.success("已批准该版主申请");
    }

    /**
     * Song：管理员拒绝申请
     * Song：说明
     */
    @PostMapping("/reject/{id}")
    public Result<?> reject(@PathVariable Long id, @Valid @RequestBody ModeratorReviewReq body) {
        ensureAdmin();
        String reviewNote = body != null ? body.getReviewNote() : null;
        moderatorApplicationService.reject(id, reviewNote);
        return Result.success("已拒绝该版主申请");
    }

    private void ensureAdmin() {
        if (!PermissionUtils.isAdmin()) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "仅管理员可执行该操作");
        }
    }
}
