package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.entity.SysAnnouncement;
import com.campus.trend.campus_pulse.entity.SysUserPopupLog;
import com.campus.trend.campus_pulse.mapper.SysAnnouncementMapper;
import com.campus.trend.campus_pulse.mapper.SysUserPopupLogMapper;
import com.campus.trend.campus_pulse.service.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    @Autowired
    private SysAnnouncementMapper announcementMapper;

    @Autowired
    private SysUserPopupLogMapper popupLogMapper;

    @Override
    public SysAnnouncement getPendingPopup(String userId) {
        // 获取最新的欢迎弹窗
        SysAnnouncement welcome = announcementMapper.selectOne(new LambdaQueryWrapper<SysAnnouncement>()
                .eq(SysAnnouncement::getType, "WELCOME")
                .eq(SysAnnouncement::getIsActive, 1)
                .orderByDesc(SysAnnouncement::getCreateTime)
                .last("LIMIT 1"));
        
        if (welcome == null) return null;

        // 检查用户是否已读
        SysUserPopupLog log = popupLogMapper.selectOne(new LambdaQueryWrapper<SysUserPopupLog>()
                .eq(SysUserPopupLog::getUserId, userId)
                .eq(SysUserPopupLog::getAnnouncementId, welcome.getId()));
        
        return log == null ? welcome : null;
    }

    @Override
    public void markAsSeen(String userId, Long announcementId) {
        SysUserPopupLog log = new SysUserPopupLog();
        log.setUserId(userId);
        log.setAnnouncementId(announcementId);
        log.setShowTime(LocalDateTime.now());
        popupLogMapper.insert(log);
    }

    @Override
    public void saveAnnouncement(SysAnnouncement announcement) {
        if (announcement.getId() == null) {
            announcementMapper.insert(announcement);
        } else {
            announcementMapper.updateById(announcement);
        }
    }
}
