package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SysNotification;

public interface SysNotificationService extends IService<SysNotification> {
    void createNotification(String userId, String senderId, String senderName, String senderAvatar, String title, String content, Integer type, String relatedId);
    long getUnreadCount(String userId);
    void markAsRead(String id);
    void markAllAsRead(String userId);
}
