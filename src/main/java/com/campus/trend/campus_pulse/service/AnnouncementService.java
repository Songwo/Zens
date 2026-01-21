package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.entity.Announcement;

public interface AnnouncementService {
    /**
     * 获取用户当前需要显示的弹窗公告
     */
    Announcement getPendingPopup(String userId);

    /**
     * 标记用户已读弹窗
     */
    void markAsSeen(String userId, Long announcementId);

    /**
     * 发布/修改公告 (管理员)
     */
    void saveAnnouncement(Announcement announcement);
}
