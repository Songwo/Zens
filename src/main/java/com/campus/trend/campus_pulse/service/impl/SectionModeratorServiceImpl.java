package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.entity.ModeratorApplication;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.SysReport;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.mapper.ModeratorApplicationMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.SectionModeratorService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SectionModeratorServiceImpl implements SectionModeratorService {

    private static final int APPLICATION_STATUS_APPROVED = 1;

    private final ModeratorApplicationMapper moderatorApplicationMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;

    @Override
    public Set<Long> getModeratedSectionIds(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Collections.emptySet();
        }
        return moderatorApplicationMapper.selectList(new LambdaQueryWrapper<ModeratorApplication>()
                        .select(ModeratorApplication::getSectionId)
                        .eq(ModeratorApplication::getUserId, userId)
                        .eq(ModeratorApplication::getStatus, APPLICATION_STATUS_APPROVED))
                .stream()
                .map(ModeratorApplication::getSectionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public boolean hasModeratorCapability(String userId) {
        return PermissionUtils.isUserAdminOrModerator(userId) || !getModeratedSectionIds(userId).isEmpty();
    }

    @Override
    public boolean isSectionModerator(String userId, Long sectionId) {
        if (!StringUtils.hasText(userId) || sectionId == null) {
            return false;
        }
        return getModeratedSectionIds(userId).contains(sectionId);
    }

    @Override
    public boolean canModerateSection(String userId, Long sectionId) {
        if (PermissionUtils.isUserAdminOrModerator(userId)) {
            return true;
        }
        return isSectionModerator(userId, sectionId);
    }

    @Override
    public boolean canModeratePost(String userId, String postId) {
        if (!StringUtils.hasText(postId)) {
            return false;
        }
        if (PermissionUtils.isUserAdminOrModerator(userId)) {
            return true;
        }
        Post post = postMapper.selectById(postId);
        return post != null && canModerateSection(userId, post.getSectionId());
    }

    @Override
    public boolean canModerateComment(String userId, String commentId) {
        if (!StringUtils.hasText(commentId)) {
            return false;
        }
        if (PermissionUtils.isUserAdminOrModerator(userId)) {
            return true;
        }
        Comment comment = commentMapper.selectById(commentId);
        return comment != null && canModeratePost(userId, comment.getPostId());
    }

    @Override
    public boolean canModerateReport(String userId, SysReport report) {
        if (report == null) {
            return false;
        }
        if (PermissionUtils.isUserAdminOrModerator(userId)) {
            return true;
        }
        if ("post".equalsIgnoreCase(report.getTargetType())) {
            return canModeratePost(userId, report.getTargetId());
        }
        if ("comment".equalsIgnoreCase(report.getTargetType())) {
            return canModerateComment(userId, report.getTargetId());
        }
        return false;
    }

    @Override
    public Long resolveReportSectionId(SysReport report) {
        if (report == null || !StringUtils.hasText(report.getTargetType()) || !StringUtils.hasText(report.getTargetId())) {
            return null;
        }
        if ("post".equalsIgnoreCase(report.getTargetType())) {
            Post post = postMapper.selectById(report.getTargetId());
            return post != null ? post.getSectionId() : null;
        }
        if ("comment".equalsIgnoreCase(report.getTargetType())) {
            Comment comment = commentMapper.selectById(report.getTargetId());
            if (comment == null || !StringUtils.hasText(comment.getPostId())) {
                return null;
            }
            Post post = postMapper.selectById(comment.getPostId());
            return post != null ? post.getSectionId() : null;
        }
        return null;
    }
}
