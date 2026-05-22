package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.ReportManageResp;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.SysReport;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.ReportWorkflowService;
import com.campus.trend.campus_pulse.service.SectionModeratorService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import com.campus.trend.campus_pulse.service.SysReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Song：举报管理控制器
 */
@Tag(name = "举报管理")
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private static final String AUDIT_STATUS_DELETED = "DELETED";
    private static final int POST_STATUS_PUBLISHED = 1;
    private static final int REPORT_STATUS_PENDING = 0;
    private static final int REPORT_STATUS_QUEUED = 10;
    private static final int REPORT_STATUS_PROCESSING = 11;

    private final SysReportService sysReportService;
    private final ReportWorkflowService reportWorkflowService;
    private final SectionModeratorService sectionModeratorService;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;

    /**
     * Song：提交举报
     */
    @Operation(summary = "提交举报")
    @PostMapping("/create")
    public Result<?> createReport(@RequestBody SysReport report) {
        if (report == null || !StringUtils.hasText(report.getTargetId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "举报对象不能为空");
        }
        String targetType = StringUtils.hasText(report.getTargetType())
                ? report.getTargetType().trim().toLowerCase()
                : "";
        if (!"post".equals(targetType) && !"comment".equals(targetType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "举报对象类型非法");
        }
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String reporterId = authUser.getUser().getId();
        validateReportTarget(targetType, report.getTargetId(), reporterId);
        ensureNoPendingDuplicate(targetType, report.getTargetId(), reporterId);
        report.setTargetType(targetType);
        report.setReporterId(reporterId);
        report.setStatus(REPORT_STATUS_PENDING);
        report.setCreateTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        sysReportService.save(report);
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

    private void validateReportTarget(String targetType, String targetId, String reporterId) {
        if ("post".equals(targetType)) {
            Post post = postMapper.selectById(targetId);
            if (post == null || isDeletedPost(post) || !Integer.valueOf(POST_STATUS_PUBLISHED).equals(post.getStatus())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "举报对象不存在或已不可见");
            }
            if (reporterId.equals(post.getUserId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "不能举报自己发布的内容");
            }
            return;
        }

        Comment comment = commentMapper.selectById(targetId);
        if (comment == null || !StringUtils.hasText(comment.getPostId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "举报对象不存在");
        }
        if (reporterId.equals(comment.getUserId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能举报自己发布的内容");
        }
        Post post = postMapper.selectById(comment.getPostId());
        if (post == null || isDeletedPost(post) || !Integer.valueOf(POST_STATUS_PUBLISHED).equals(post.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "评论所属帖子不存在或已不可见");
        }
    }

    private void ensureNoPendingDuplicate(String targetType, String targetId, String reporterId) {
        long pendingCount = sysReportService.lambdaQuery()
                .eq(SysReport::getTargetType, targetType)
                .eq(SysReport::getTargetId, targetId)
                .eq(SysReport::getReporterId, reporterId)
                .in(SysReport::getStatus, REPORT_STATUS_PENDING, REPORT_STATUS_QUEUED, REPORT_STATUS_PROCESSING)
                .count();
        if (pendingCount > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该举报正在处理中，请勿重复提交");
        }
    }

    private boolean isDeletedPost(Post post) {
        return post != null && AUDIT_STATUS_DELETED.equalsIgnoreCase(post.getAuditStatus());
    }
}
