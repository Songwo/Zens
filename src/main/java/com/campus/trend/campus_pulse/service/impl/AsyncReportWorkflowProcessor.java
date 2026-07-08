package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.SysReport;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.SysReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncReportWorkflowProcessor {

    private static final int STATUS_RESOLVED = 1;
    private static final int STATUS_IGNORED = 2;
    private static final int STATUS_REJECTED = 3;
    private static final int STATUS_PROCESSING = 11;


    private final SysReportService sysReportService;
    private final PostMapper postMapper;
    private final NotificationService notificationService;
    private final com.campus.trend.campus_pulse.service.post.PostCacheManager postCacheManager;
    private final com.campus.trend.campus_pulse.service.AsyncTaskService asyncTaskService;

    @Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void processResolve(String reportId, Integer finalStatus, String operatorUserId) {
        SysReport report = sysReportService.getById(reportId);
        if (report == null) {
            log.warn("异步处理举报时未找到记录: reportId={}, operator={}", reportId, operatorUserId);
            return;
        }
        transition(report, STATUS_PROCESSING);
        report.setStatus(STATUS_IGNORED == finalStatus ? STATUS_IGNORED : STATUS_RESOLVED);
        report.setUpdateTime(LocalDateTime.now());
        sysReportService.updateById(report);
        log.info("举报异步处理完成: reportId={}, finalStatus={}, operator={}", reportId, report.getStatus(), operatorUserId);
    }

    @Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void processRejectPost(String reportId, String reason, String operatorUserId) {
        SysReport report = sysReportService.getById(reportId);
        if (report == null) {
            log.warn("异步打回举报时未找到记录: reportId={}, operator={}", reportId, operatorUserId);
            return;
        }
        transition(report, STATUS_PROCESSING);
        if (!"post".equals(report.getTargetType())) {
            throw new IllegalArgumentException("仅支持打回帖子类型的举报");
        }

        Post post = postMapper.selectById(report.getTargetId());
        if (post == null) {
            throw new IllegalArgumentException("帖子不存在");
        }

        String finalReason = StringUtils.hasText(reason) ? reason : report.getReason();
        post.setAuditStatus("REJECTED");
        post.setStatus(0);
        post.setRejectReason(StringUtils.hasText(finalReason) ? finalReason : null);
        post.setIsPinned(0);
        post.setGlobalPin(0);
        post.setCategoryPin(0);
        post.setPinOrder(0);
        post.setPinExpireAt(null);
        post.setIsFeatured(0);
        post.setUpdateTime(LocalDateTime.now());
        postMapper.updateById(post);
        invalidatePostCache(post.getSectionId(), post.getId());
        // Song：同步 Meilisearch —— 打回后帖子不再可公开索引，触发索引删除，避免搜索结果残留
        asyncTaskService.syncPostToSearchAsync(post.getId());

        report.setStatus(STATUS_REJECTED);
        report.setUpdateTime(LocalDateTime.now());
        sysReportService.updateById(report);

        String title = "帖子被打回修改";
        String content = String.format(
                "您的帖子「%s」因被举报已被审核人员打回，请及时修改后重新发布。打回原因：%s。如未在规定时间内修改，帖子将被删除。",
                post.getTitle() != null ? post.getTitle() : "无标题",
                StringUtils.hasText(finalReason) ? finalReason : "请根据社区规范调整内容");

        notificationService.createNotification(post.getUserId(), "system", title, content, post.getId(), null);
        log.info("举报异步打回完成: reportId={}, postId={}, operator={}", reportId, post.getId(), operatorUserId);
    }

    private void transition(SysReport report, int nextStatus) {
        report.setStatus(nextStatus);
        report.setUpdateTime(LocalDateTime.now());
        sysReportService.updateById(report);
    }

    private void invalidatePostCache(Long sectionId, String postId) {
        postCacheManager.invalidatePostCaches(sectionId, postId);
    }
}
