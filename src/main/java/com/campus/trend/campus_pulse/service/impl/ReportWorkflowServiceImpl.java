package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.entity.SysReport;
import com.campus.trend.campus_pulse.service.ReportWorkflowService;
import com.campus.trend.campus_pulse.service.SysReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportWorkflowServiceImpl implements ReportWorkflowService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_RESOLVED = 1;
    private static final int STATUS_IGNORED = 2;
    private static final int STATUS_QUEUED = 10;
    private static final Set<Integer> ALLOWED_FINAL_STATUS = Set.of(STATUS_RESOLVED, STATUS_IGNORED);

    private final SysReportService sysReportService;
    private final AsyncReportWorkflowProcessor asyncReportWorkflowProcessor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void queueResolve(String reportId, Integer finalStatus, String operatorUserId) {
        if (!ALLOWED_FINAL_STATUS.contains(finalStatus)) {
            throw new IllegalArgumentException("举报处理状态非法");
        }
        SysReport report = loadPendingReport(reportId);
        report.setStatus(STATUS_QUEUED);
        report.setUpdateTime(LocalDateTime.now());
        sysReportService.updateById(report);
        asyncReportWorkflowProcessor.processResolve(reportId, finalStatus, operatorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void queueRejectPost(String reportId, String reason, String operatorUserId) {
        SysReport report = loadPendingReport(reportId);
        report.setStatus(STATUS_QUEUED);
        report.setUpdateTime(LocalDateTime.now());
        sysReportService.updateById(report);
        asyncReportWorkflowProcessor.processRejectPost(reportId, reason, operatorUserId);
    }

    private SysReport loadPendingReport(String reportId) {
        SysReport report = sysReportService.getById(reportId);
        if (report == null) {
            throw new IllegalArgumentException("举报记录不存在");
        }
        if (report.getStatus() != null && report.getStatus() != STATUS_PENDING) {
            throw new IllegalArgumentException("举报已进入处理流程，请勿重复提交");
        }
        return report;
    }
}
