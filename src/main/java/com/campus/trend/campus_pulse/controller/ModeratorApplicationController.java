package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.ModeratorApplicationService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    public Result<?> apply(@RequestBody Map<String, Object> body) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        Long sectionId = Long.valueOf(body.get("sectionId").toString());
        String reason = (String) body.get("reason");

        if (reason == null || reason.isBlank()) {
            return Result.failed("请填写申请理由");
        }

        moderatorApplicationService.apply(userId, sectionId, reason);
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
    public Result<?> pendingApplications() {
        return Result.success(moderatorApplicationService.getPendingApplications());
    }

    /**
     * Song：管理员批准申请
     * Song：说明
     */
    @PostMapping("/approve/{id}")
    public Result<?> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reviewNote = body != null ? body.get("reviewNote") : null;
        moderatorApplicationService.approve(id, reviewNote);
        return Result.success("已批准该版主申请");
    }

    /**
     * Song：管理员拒绝申请
     * Song：说明
     */
    @PostMapping("/reject/{id}")
    public Result<?> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reviewNote = body != null ? body.get("reviewNote") : null;
        moderatorApplicationService.reject(id, reviewNote);
        return Result.success("已拒绝该版主申请");
    }
}
