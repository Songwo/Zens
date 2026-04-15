package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.entity.SysReport;

import java.util.Set;

public interface SectionModeratorService {

    Set<Long> getModeratedSectionIds(String userId);

    boolean hasModeratorCapability(String userId);

    boolean isSectionModerator(String userId, Long sectionId);

    boolean canModerateSection(String userId, Long sectionId);

    boolean canModeratePost(String userId, String postId);

    boolean canModerateComment(String userId, String commentId);

    boolean canModerateReport(String userId, SysReport report);

    Long resolveReportSectionId(SysReport report);
}
