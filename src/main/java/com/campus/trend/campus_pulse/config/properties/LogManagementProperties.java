package com.campus.trend.campus_pulse.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "campus.log-management")
public class LogManagementProperties {

    private boolean enabled = true;
    private String baseDir = "./log";
    private String backupDir = "./log/backups";
    private int listLimit = 200;
    private int previewMaxLines = 400;
    private int previewMaxBytes = 131072;
    private int defaultPreviewLines = 200;
    private int backupRetentionDays = 15;
    private int maxBackupFiles = 30;
    private long maxBackupTotalSizeMb = 1024;
    private boolean autoBackupEnabled = false;
    private String autoBackupCron = "0 0 */6 * * ?";
    private String cleanupCron = "0 20 4 * * ?";
    private List<String> allowedExtensions = new ArrayList<>(List.of(".log", ".txt", ".out", ".err", ".gz"));
}
