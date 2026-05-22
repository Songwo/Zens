package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.dto.response.LogBackupResp;
import com.campus.trend.campus_pulse.dto.response.LogCleanupResp;
import com.campus.trend.campus_pulse.dto.response.LogContentResp;
import com.campus.trend.campus_pulse.dto.response.LogFileSummaryResp;
import com.campus.trend.campus_pulse.dto.response.LogOverviewResp;
import com.campus.trend.campus_pulse.service.LogManagementService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/admin/logs", "/api/admin/logs"})
public class LogManagementController {

    private final LogManagementService logManagementService;

    @GetMapping("/overview")
    public Result<LogOverviewResp> getOverview() {
        Result<LogOverviewResp> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(logManagementService.getOverview());
    }

    @GetMapping("/files")
    public Result<List<LogFileSummaryResp>> listFiles(@RequestParam(value = "keyword", required = false) String keyword) {
        Result<List<LogFileSummaryResp>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(logManagementService.listFiles(keyword));
    }

    @GetMapping("/content")
    public Result<LogContentResp> getContent(@RequestParam("file") String file,
                                             @RequestParam(value = "mode", required = false) String mode,
                                             @RequestParam(value = "lines", required = false) Integer lines,
                                             @RequestParam(value = "maxBytes", required = false) Integer maxBytes) {
        Result<LogContentResp> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(logManagementService.readContent(file, mode, lines, maxBytes));
    }

    @PostMapping("/backup")
    public Result<LogBackupResp> createBackup() {
        Result<LogBackupResp> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(logManagementService.createBackupSnapshot());
    }

    @PostMapping("/cleanup")
    public Result<LogCleanupResp> cleanup() {
        Result<LogCleanupResp> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(logManagementService.cleanupExpiredFiles());
    }

    @DeleteMapping("/file")
    public Result<?> deleteFile(@RequestParam("file") String file) {
        Result<?> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        logManagementService.deleteFile(file);
        return Result.success("日志文件已删除");
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam("file") String file) throws IOException {
        if (!PermissionUtils.isAdmin()) {
            byte[] body = "{\"code\":3003,\"message\":\"仅管理员可下载日志文件\"}".getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.status(403)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InputStreamResource(new ByteArrayInputStream(body)));
        }

        Path path = logManagementService.resolveFileForDownload(file);
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(path));
        String fileName = path.getFileName().toString().toLowerCase();
        MediaType mediaType = fileName.endsWith(".log") || fileName.endsWith(".txt")
                || fileName.endsWith(".out") || fileName.endsWith(".err")
                ? MediaType.TEXT_PLAIN
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(Files.size(path))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(path.getFileName().toString(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    private <T> Result<T> requireAdmin() {
        if (!PermissionUtils.isAdmin()) {
            return Result.error(ResultCode.NO_PERMISSION, "仅管理员可执行日志管理操作");
        }
        return null;
    }
}
