package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.ReportManageResp;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.entity.SysReport;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.mapper.SysReportMapper;
import com.campus.trend.campus_pulse.service.AsyncTaskService;
import com.campus.trend.campus_pulse.service.SectionModeratorService;
import com.campus.trend.campus_pulse.service.SysReportService;
import com.campus.trend.campus_pulse.service.TrustLevelService;
import com.campus.trend.campus_pulse.config.properties.TrustLevelProperties;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SysReportServiceImpl extends ServiceImpl<SysReportMapper, SysReport> implements SysReportService {

    private final SectionModeratorService sectionModeratorService;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final SectionMapper sectionMapper;
    private final TrustLevelService trustLevelService;
    private final TrustLevelProperties trustLevelProperties;
    private final AsyncTaskService asyncTaskService;

    private static final String AUDIT_STATUS_DELETED = "DELETED";
    private static final String AUDIT_STATUS_PENDING = "PENDING";
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";
    private static final int POST_STATUS_PUBLISHED = 1;
    private static final int POST_STATUS_DRAFT = 0;
    private static final int REPORT_STATUS_PENDING = 0;
    private static final int REPORT_STATUS_QUEUED = 10;
    private static final int REPORT_STATUS_PROCESSING = 11;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createReport(SysReport report, String reporterId) {
        if (report == null || !StringUtils.hasText(report.getTargetId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "举报对象不能为空");
        }
        String targetType = StringUtils.hasText(report.getTargetType())
                ? report.getTargetType().trim().toLowerCase()
                : "";
        if (!"post".equals(targetType) && !"comment".equals(targetType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "举报对象类型非法");
        }
        validateReportTarget(targetType, report.getTargetId(), reporterId);
        ensureNoPendingDuplicate(targetType, report.getTargetId(), reporterId);
        // Song：写入举报人的信任等级与 flag 权重（自治 flag 加权机制）
        int tl = trustLevelService.getTrustLevel(reporterId);
        int weight = trustLevelService.flagWeight(reporterId);
        report.setTargetType(targetType);
        report.setReporterId(reporterId);
        report.setReporterTrustLevel(tl);
        report.setFlagWeight(weight);
        report.setStatus(REPORT_STATUS_PENDING);
        report.setCreateTime(java.time.LocalDateTime.now());
        report.setUpdateTime(java.time.LocalDateTime.now());
        save(report);

        // Song：聚合该目标所有未忽略举报的权重总和，达到阈值自动隐藏（社区自治）
        try {
            int totalWeight = baseMapper.sumFlagWeight(targetType, report.getTargetId());
            int threshold = trustLevelProperties.getFlag().getAutoHideThreshold();
            if (totalWeight >= threshold) {
                autoHideForFlags(targetType, report.getTargetId(), totalWeight);
            }
        } catch (Exception e) {
            log.warn("自治 flag 聚合判定失败 targetType={}, targetId={}, err={}",
                    targetType, report.getTargetId(), e.getMessage());
        }
    }

    /**
     * Song：自治隐藏 —— 当某目标的 flag 权重总和达到阈值，自动将帖子置为 PENDING 等待管理员确认，
     * 并通知作者。仅对 APPROVED 状态的帖子生效，避免重复处理。
     */
    private void autoHideForFlags(String targetType, String targetId, int totalWeight) {
        if (!"post".equals(targetType)) {
            // Song：评论暂不自动隐藏（影响面小），仅帖子走自治流程
            return;
        }
        Post post = postMapper.selectById(targetId);
        if (post == null || !AUDIT_STATUS_APPROVED.equalsIgnoreCase(post.getAuditStatus())) {
            return;
        }
        post.setAuditStatus(AUDIT_STATUS_PENDING);
        post.setUpdateTime(java.time.LocalDateTime.now());
        postMapper.updateById(post);
        log.info("自治隐藏触发: postId={}, 累计权重={}, 阈值={}", targetId, totalWeight,
                trustLevelProperties.getFlag().getAutoHideThreshold());

        // Song：异步通知作者内容被社区标记，进入人工复核
        try {
            asyncTaskService.sendSystemNotificationAsync(post.getUserId(),
                    "你的帖子被社区标记",
                    String.format("你的帖子「%s」被多位高信任等级用户标记，已暂时隐藏等待管理员复核。如有异议请联系管理员。",
                            post.getTitle()),
                    post.getId());
        } catch (Exception e) {
            log.warn("自治隐藏通知作者失败 postId={}", targetId);
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
        long pendingCount = lambdaQuery()
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

    @Override
    public IPage<ReportManageResp> getManagePage(String userId, Integer current, Integer size, Integer status, Long sectionId) {
        if (!sectionModeratorService.hasModeratorCapability(userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权访问举报管理");
        }

        Page<SysReport> page = new Page<>(current != null && current > 0 ? current : 1, size != null && size > 0 ? size : 10);
        LambdaQueryWrapper<SysReport> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(SysReport::getStatus, status);
        }
        applyModerationScope(userId, wrapper, sectionId);
        wrapper.orderByDesc(SysReport::getCreateTime);

        IPage<SysReport> reportPage = this.page(page, wrapper);
        Page<ReportManageResp> resultPage = new Page<>(reportPage.getCurrent(), reportPage.getSize(), reportPage.getTotal());
        resultPage.setRecords(enrichReports(reportPage.getRecords()));
        return resultPage;
    }

    @Override
    public SysReport getAccessibleReport(String userId, String reportId) {
        SysReport report = getById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "举报记录不存在");
        }
        if (!sectionModeratorService.canModerateReport(userId, report)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权处理该举报");
        }
        return report;
    }

    private void applyModerationScope(String userId, LambdaQueryWrapper<SysReport> wrapper, Long sectionId) {
        if (PermissionUtils.isUserAdmin(userId) && sectionId == null) {
            return;
        }

        Set<Long> scopedSectionIds = resolveScopedSectionIds(userId, sectionId);
        appendSectionScope(wrapper, scopedSectionIds);
    }

    private Set<Long> resolveScopedSectionIds(String userId, Long sectionId) {
        if (PermissionUtils.isUserAdmin(userId)) {
            return sectionId == null
                    ? Collections.emptySet()
                    : Collections.singleton(sectionId);
        }

        Set<Long> moderatedSectionIds = sectionModeratorService.getModeratedSectionIds(userId);
        if (moderatedSectionIds.isEmpty()) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权访问举报管理");
        }
        if (sectionId == null) {
            return moderatedSectionIds;
        }
        if (!moderatedSectionIds.contains(sectionId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权查看该板块的举报");
        }
        return Collections.singleton(sectionId);
    }

    private void appendSectionScope(LambdaQueryWrapper<SysReport> wrapper, Set<Long> sectionIds) {
        if (sectionIds == null || sectionIds.isEmpty()) {
            wrapper.eq(SysReport::getId, "__EMPTY__");
            return;
        }

        String sectionIdSql = sectionIds.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (!StringUtils.hasText(sectionIdSql)) {
            wrapper.eq(SysReport::getId, "__EMPTY__");
            return;
        }

        wrapper.and(scope -> scope.apply("""
                (
                    LOWER(target_type) = 'post'
                    AND EXISTS (
                        SELECT 1
                        FROM sys_post p
                        WHERE p.id = sys_report.target_id
                          AND p.section_id IN (%s)
                    )
                )
                OR (
                    LOWER(target_type) = 'comment'
                    AND EXISTS (
                        SELECT 1
                        FROM sys_comment c
                        JOIN sys_post p ON p.id = c.post_id
                        WHERE c.id = sys_report.target_id
                          AND p.section_id IN (%s)
                    )
                )
                """.formatted(sectionIdSql, sectionIdSql)));
    }

    private List<ReportManageResp> enrichReports(List<SysReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> postIds = reports.stream()
                .filter(report -> "post".equalsIgnoreCase(report.getTargetType()))
                .map(SysReport::getTargetId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> commentIds = reports.stream()
                .filter(report -> "comment".equalsIgnoreCase(report.getTargetType()))
                .map(SysReport::getTargetId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Comment> commentMap = commentIds.isEmpty()
                ? Collections.emptyMap()
                : commentMapper.selectBatchIds(commentIds).stream()
                .collect(Collectors.toMap(Comment::getId, comment -> comment, (left, right) -> left));

        Set<String> commentPostIds = commentMap.values().stream()
                .map(Comment::getPostId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        postIds.addAll(commentPostIds);

        Map<String, Post> postMap = postIds.isEmpty()
                ? Collections.emptyMap()
                : postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, post -> post, (left, right) -> left));

        Set<Long> sectionIds = postMap.values().stream()
                .map(Post::getSectionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Section> sectionMap = sectionIds.isEmpty()
                ? Collections.emptyMap()
                : sectionMapper.selectBatchIds(sectionIds).stream()
                .collect(Collectors.toMap(Section::getId, section -> section, (left, right) -> left));

        List<ReportManageResp> result = new ArrayList<>(reports.size());
        for (SysReport report : reports) {
            result.add(toManageResp(report, postMap, commentMap, sectionMap));
        }
        return result;
    }

    private ReportManageResp toManageResp(SysReport report,
                                          Map<String, Post> postMap,
                                          Map<String, Comment> commentMap,
                                          Map<Long, Section> sectionMap) {
        ReportManageResp resp = new ReportManageResp();
        resp.setId(report.getId());
        resp.setTargetType(report.getTargetType());
        resp.setTargetId(report.getTargetId());
        resp.setReason(report.getReason());
        resp.setDetails(report.getDetails());
        resp.setReporterId(report.getReporterId());
        resp.setStatus(report.getStatus());
        resp.setCreateTime(report.getCreateTime());
        resp.setUpdateTime(report.getUpdateTime());

        Post post = null;
        if ("post".equalsIgnoreCase(report.getTargetType())) {
            post = postMap.get(report.getTargetId());
            if (post != null) {
                resp.setTargetTitle(post.getTitle());
                resp.setTargetPreview(clip(post.getSummary()));
            }
        } else if ("comment".equalsIgnoreCase(report.getTargetType())) {
            Comment comment = commentMap.get(report.getTargetId());
            if (comment != null) {
                post = postMap.get(comment.getPostId());
                resp.setTargetTitle(post != null && StringUtils.hasText(post.getTitle()) ? post.getTitle() : "评论所属帖子");
                resp.setTargetPreview(clip(comment.getContent()));
            }
        }

        if (post != null) {
            resp.setSectionId(post.getSectionId());
            Section section = sectionMap.get(post.getSectionId());
            if (section != null) {
                resp.setSectionName(section.getName());
            }
        }

        return resp;
    }

    private String clip(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }
}
