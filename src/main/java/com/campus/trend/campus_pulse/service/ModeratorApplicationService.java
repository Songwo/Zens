package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.ModeratorApplication;

import java.util.List;

public interface ModeratorApplicationService extends IService<ModeratorApplication> {

    /**
     * Song：用户申请版主
     */
    void apply(String userId, Long sectionId, String reason);

    /**
     * Song：获取当前用户的申请列表
     */
    List<ModeratorApplication> getMyApplications(String userId);

    /**
     * Song：管理员获取所有待审核列表
     */
    List<ModeratorApplication> getPendingApplications();

    /**
     * Song：管理员批准
     */
    void approve(Long applicationId, String reviewNote);

    /**
     * Song：管理员拒绝
     */
    void reject(Long applicationId, String reviewNote);
}
