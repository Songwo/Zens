package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.SysNotification;
import com.campus.trend.campus_pulse.mapper.SysNotificationMapper;
import com.campus.trend.campus_pulse.service.SysNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SysNotificationServiceImpl extends ServiceImpl<SysNotificationMapper, SysNotification> implements SysNotificationService {

    @Async
    @Override
    public void createNotification(String userId, String senderId, String senderName, String senderAvatar, String title, String content, Integer type, String relatedId) {
        // Don't notify self
        if (userId.equals(senderId)) return;

        SysNotification notification = new SysNotification();
        notification.setUserId(userId);
        notification.setSenderId(senderId);
        notification.setSenderName(senderName);
        notification.setSenderAvatar(senderAvatar);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setRelatedId(relatedId);
        notification.setStatus(0); // Unread
        notification.setCreateTime(LocalDateTime.now());
        
        this.save(notification);
    }

    @Override
    public long getUnreadCount(String userId) {
        return this.count(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getStatus, 0));
    }

    @Override
    public void markAsRead(String id) {
        SysNotification notification = new SysNotification();
        notification.setId(id);
        notification.setStatus(1);
        this.updateById(notification);
    }

    @Override
    public void markAllAsRead(String userId) {
        this.update(new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .set(SysNotification::getStatus, 1));
    }
}
