package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

@Data
public class LogOverviewResp {

    private String baseDir;
    private String backupDir;
    private String activeFileName;
    private String activeFilePath;
    private boolean activeFileAccessible;
    private String archivePattern;
    private String rollingMaxFileSize;
    private Integer rollingMaxHistory;
    private String rollingTotalSizeCap;
    private Boolean cleanHistoryOnStart;
    private boolean autoBackupEnabled;
    private String autoBackupCron;
    private String cleanupCron;
    private int backupRetentionDays;
    private int maxBackupFiles;
    private long maxBackupTotalSizeBytes;
    private int previewMaxLines;
    private int previewMaxBytes;
    private int listLimit;
    private long totalFiles;
    private long totalSizeBytes;
    private long archiveFiles;
    private long backupFiles;
    private long compressedFiles;
    private long activeSizeBytes;
}
