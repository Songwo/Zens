package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.entity.Announcement;
import com.campus.trend.campus_pulse.entity.UserPopupLog;
import com.campus.trend.campus_pulse.mapper.AnnouncementMapper;
import com.campus.trend.campus_pulse.mapper.UserPopupLogMapper;
import com.campus.trend.campus_pulse.service.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    @Autowired
    private AnnouncementMapper announcementMapper;

    @Autowired
    private UserPopupLogMapper popupLogMapper;

    @Override
    public Announcement getPendingPopup(String userId) {
        // 获取最新的欢迎弹窗
        Announcement welcome = announcementMapper.selectOne(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getType, "WELCOME")
                .eq(Announcement::getIsActive, 1)
                .orderByDesc(Announcement::getCreateTime)
                .last("LIMIT 1"));
        
        if (welcome == null) return null;

        // 检查用户是否已读
        UserPopupLog log = popupLogMapper.selectOne(new LambdaQueryWrapper<UserPopupLog>()
                .eq(UserPopupLog::getUserId, userId)
                .eq(UserPopupLog::getAnnouncementId, welcome.getId()));
        
        return log == null ? welcome : null;
    }

    @Override
    public void markAsSeen(String userId, Long announcementId) {
        UserPopupLog log = new UserPopupLog();
        log.setUserId(userId);
        log.setAnnouncementId(announcementId);
        log.setShowTime(LocalDateTime.now());
        popupLogMapper.insert(log);
    }

    @Override
    public void saveAnnouncement(Announcement announcement) {
        if (announcement.getId() == null) {
            announcementMapper.insert(announcement);
        } else {
            announcementMapper.updateById(announcement);
        }
    }
}
