package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.config.properties.LogManagementProperties;
import com.campus.trend.campus_pulse.dto.response.LogCleanupResp;
import com.campus.trend.campus_pulse.dto.response.LogContentResp;
import com.campus.trend.campus_pulse.service.impl.LogManagementServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogManagementServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void readContent_shouldReturnTailPreview() throws Exception {
        Path logDir = Files.createDirectories(tempDir.resolve("log"));
        Path activeFile = logDir.resolve("logfile.log");
        Files.writeString(activeFile, IntStream.rangeClosed(1, 200)
                .mapToObj(index -> "line-" + index)
                .collect(Collectors.joining(System.lineSeparator())));

        LogManagementServiceImpl service = createService(logDir, logDir.resolve("backups"), activeFile);

        LogContentResp resp = service.readContent("base/logfile.log", "tail", 10, 4096);

        assertThat(resp.getMode()).isEqualTo("tail");
        assertThat(resp.getLineCount()).isEqualTo(20);
        assertThat(resp.getContent()).contains("line-200");
        assertThat(resp.getContent()).contains("line-181");
        assertThat(resp.getContent()).doesNotContain("line-180");
    }

    @Test
    void cleanupExpiredFiles_shouldDeleteOldAndOverflowBackups() throws Exception {
        Path logDir = Files.createDirectories(tempDir.resolve("log"));
        Path backupDir = Files.createDirectories(logDir.resolve("backups"));
        Path activeFile = logDir.resolve("logfile.log");
        Files.writeString(activeFile, "active-log");

        Path oldBackup = backupDir.resolve("old.gz");
        Path recentBackupA = backupDir.resolve("recent-a.gz");
        Path recentBackupB = backupDir.resolve("recent-b.gz");

        Files.writeString(oldBackup, "old");
        Files.writeString(recentBackupA, "recent-a");
        Files.writeString(recentBackupB, "recent-b");

        Files.setLastModifiedTime(oldBackup, FileTime.from(Instant.now().minusSeconds(30L * 86400)));
        Files.setLastModifiedTime(recentBackupA, FileTime.from(Instant.now().minusSeconds(3600)));
        Files.setLastModifiedTime(recentBackupB, FileTime.from(Instant.now().minusSeconds(7200)));

        LogManagementProperties properties = new LogManagementProperties();
        properties.setBaseDir(logDir.toString());
        properties.setBackupDir(backupDir.toString());
        properties.setBackupRetentionDays(10);
        properties.setMaxBackupFiles(1);
        properties.setMaxBackupTotalSizeMb(100);

        LogManagementServiceImpl service = createService(properties, activeFile);

        LogCleanupResp resp = service.cleanupExpiredFiles();

        assertThat(resp.getScannedFiles()).isEqualTo(3);
        assertThat(resp.getDeletedFiles()).isEqualTo(2);
        assertThat(resp.getKeptFiles()).isEqualTo(1);
        assertThat(Files.exists(oldBackup)).isFalse();
        assertThat(Files.exists(recentBackupA)).isTrue();
        assertThat(Files.exists(recentBackupB)).isFalse();
    }

    @Test
    void resolveFileForDownload_shouldRejectPathTraversal() throws Exception {
        Path logDir = Files.createDirectories(tempDir.resolve("log"));
        Path activeFile = logDir.resolve("logfile.log");
        Files.writeString(activeFile, "active-log");

        LogManagementServiceImpl service = createService(logDir, logDir.resolve("backups"), activeFile);

        assertThatThrownBy(() -> service.resolveFileForDownload("base/../outside.log"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法");
    }

    private LogManagementServiceImpl createService(Path logDir, Path backupDir, Path activeFile) {
        LogManagementProperties properties = new LogManagementProperties();
        properties.setBaseDir(logDir.toString());
        properties.setBackupDir(backupDir.toString());
        properties.setListLimit(100);
        properties.setPreviewMaxLines(400);
        properties.setPreviewMaxBytes(131072);
        properties.setDefaultPreviewLines(200);
        return createService(properties, activeFile);
    }

    private LogManagementServiceImpl createService(LogManagementProperties properties, Path activeFile) {
        LogManagementServiceImpl service = new LogManagementServiceImpl(properties);
        ReflectionTestUtils.setField(service, "activeLogFile", activeFile.toString());
        ReflectionTestUtils.setField(service, "archivePattern", "./log/archive/campus-pulse.%d{yyyy-MM-dd}.%i.log.gz");
        ReflectionTestUtils.setField(service, "rollingMaxFileSize", "20MB");
        ReflectionTestUtils.setField(service, "rollingMaxHistory", 15);
        ReflectionTestUtils.setField(service, "rollingTotalSizeCap", "1GB");
        ReflectionTestUtils.setField(service, "cleanHistoryOnStart", true);
        return service;
    }
}
