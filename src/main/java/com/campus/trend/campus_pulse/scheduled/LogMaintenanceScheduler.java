package com.campus.trend.campus_pulse.scheduled;

import com.campus.trend.campus_pulse.config.properties.LogManagementProperties;
import com.campus.trend.campus_pulse.dto.response.LogBackupResp;
import com.campus.trend.campus_pulse.dto.response.LogCleanupResp;
import com.campus.trend.campus_pulse.service.LogManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogMaintenanceScheduler {

    private final LogManagementProperties logManagementProperties;
    private final LogManagementService logManagementService;

    @Scheduled(cron = "#{@logManagementProperties.cleanupCron}")
    public void cleanupLogs() {
        if (!logManagementProperties.isEnabled()) {
            return;
        }
        LogCleanupResp resp = logManagementService.cleanupExpiredFiles();
        log.info("定时日志清理完成：扫描 {} 个，保留 {} 个，删除 {} 个，释放 {} 字节",
                resp.getScannedFiles(), resp.getKeptFiles(), resp.getDeletedFiles(), resp.getDeletedBytes());
    }

    @Scheduled(cron = "#{@logManagementProperties.autoBackupCron}")
    public void backupLogs() {
        if (!logManagementProperties.isEnabled() || !logManagementProperties.isAutoBackupEnabled()) {
            return;
        }
        try {
            LogBackupResp resp = logManagementService.createBackupSnapshot();
            log.info("定时日志备份完成：{}", resp.getRelativePath());
        } catch (Exception e) {
            log.error("定时日志备份失败: {}", e.getMessage(), e);
        }
    }
}
