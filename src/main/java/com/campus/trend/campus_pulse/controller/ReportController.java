package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.ReportManageResp;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.SysReport;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.ReportWorkflowService;
import com.campus.trend.campus_pulse.service.SectionModeratorService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import com.campus.trend.campus_pulse.service.SysReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Song：举报管理控制器
 */
@Tag(name = "举报管理")
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final SysReportService sysReportService;
    private final ReportWorkflowService reportWorkflowService;
    private final SectionModeratorService sectionModeratorService;

    /**
     * Song：提交举报
     */
    @Operation(summary = "提交举报")
    @PostMapping("/create")
    public Result<?> createReport(@RequestBody SysReport report) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        sysReportService.createReport(report, authUser.getUser().getId());
        return Result.success("举报提交成功");
    }

    /**
     * Song：获取举报列表 (管理员)
     */
    @Operation(summary = "获取举报列表")
    @GetMapping("/list")
    public Result<IPage<ReportManageResp>> getReportList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long sectionId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        IPage<ReportManageResp> result = sysReportService.getManagePage(
                authUser.getUser().getId(),
                current,
                size,
                status,
                sectionId);
        return Result.success(result);
    }

    /**
     * Song：标记举报为已处理
     */
    @Operation(summary = "处理举报")
    @PostMapping("/resolve/{id}")
    public Result<?> resolveReport(@PathVariable String id, @RequestParam Integer status) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        ensureBackofficeAccess(authUser.getUser().getId());
        sysReportService.getAccessibleReport(authUser.getUser().getId(), id);
        reportWorkflowService.queueResolve(id, status, authUser.getUser().getId());
        return Result.success("举报已加入异步处理队列");
    }

    /**
     * Song：打回帖子为草稿状态，通知作者修改
     */
    @Operation(summary = "打回帖子")
    @PostMapping("/reject/{id}")
    public Result<?> rejectReport(@PathVariable String id, @RequestBody Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        ensureBackofficeAccess(authUser.getUser().getId());
        sysReportService.getAccessibleReport(authUser.getUser().getId(), id);
        reportWorkflowService.queueRejectPost(id, reason, authUser.getUser().getId());
        return Result.success("举报已加入异步打回队列");
    }

    private void ensureBackofficeAccess(String userId) {
        if (!sectionModeratorService.hasModeratorCapability(userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权访问举报管理");
        }
    }
}
