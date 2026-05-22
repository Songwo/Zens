package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.LogBackupResp;
import com.campus.trend.campus_pulse.dto.response.LogCleanupResp;
import com.campus.trend.campus_pulse.dto.response.LogContentResp;
import com.campus.trend.campus_pulse.dto.response.LogFileSummaryResp;
import com.campus.trend.campus_pulse.dto.response.LogOverviewResp;

import java.nio.file.Path;
import java.util.List;

public interface LogManagementService {

    LogOverviewResp getOverview();

    List<LogFileSummaryResp> listFiles(String keyword);

    LogContentResp readContent(String relativePath, String mode, Integer lines, Integer maxBytes);

    LogBackupResp createBackupSnapshot();

    LogCleanupResp cleanupExpiredFiles();

    void deleteFile(String relativePath);

    Path resolveFileForDownload(String relativePath);
}
