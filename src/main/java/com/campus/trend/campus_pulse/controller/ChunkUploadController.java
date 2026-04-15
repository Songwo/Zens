package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.utils.FileUtils;
import com.campus.trend.campus_pulse.utils.IdUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 分片上传控制器
 * 流程: init → 上传各分片(part) → merge
 */
@Slf4j
@RestController
@RequestMapping("/common/upload/chunk")
public class ChunkUploadController {

    @Value("${campus.upload.url-prefix:/uploads/}")
    private String urlPrefix;

    @Value("${campus.upload.max-video-size-mb:512}")
    private long maxVideoSizeMb;

    // uploadId -> 元信息
    private static final ConcurrentHashMap<String, ChunkMeta> SESSIONS = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService CLEANER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chunk-cleaner");
        t.setDaemon(true);
        return t;
    });

    static {
        // 每小时清理超过2小时未完成的上传会话
        CLEANER.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            SESSIONS.entrySet().removeIf(e -> {
                ChunkMeta m = e.getValue();
                if (now - m.createdAt > 2 * 3600_000L) {
                    try { deleteDir(m.tempDir); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            });
        }, 1, 1, TimeUnit.HOURS);
    }

    /** 初始化分片上传会话 */
    @PostMapping("/init")
    public Result<?> init(@RequestBody Map<String, Object> body) {
        String filename = String.valueOf(body.getOrDefault("filename", "file"));
        int totalChunks = Integer.parseInt(String.valueOf(body.getOrDefault("totalChunks", "1")));
        long fileSize = Long.parseLong(String.valueOf(body.getOrDefault("fileSize", "0")));
        String module = String.valueOf(body.getOrDefault("module", "common"));

        if (totalChunks <= 0 || totalChunks > 500) throw new BusinessException(ResultCode.VALIDATE_FAILED, "分片数量非法");
        if (fileSize > maxVideoSizeMb * 1024 * 1024) throw new BusinessException(ResultCode.VALIDATE_FAILED, "文件超过大小限制");

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Path tempDir = Paths.get(FileUtils.getProjectRootPath(), "data", "tmp", "chunks", uploadId);
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FAILED, "创建临时目录失败");
        }

        ChunkMeta meta = new ChunkMeta();
        meta.uploadId = uploadId;
        meta.filename = filename;
        meta.totalChunks = totalChunks;
        meta.module = module;
        meta.tempDir = tempDir;
        meta.createdAt = System.currentTimeMillis();
        SESSIONS.put(uploadId, meta);

        Map<String, String> data = new HashMap<>();
        data.put("uploadId", uploadId);
        return Result.success(data);
    }

    /** 上传单个分片 */
    @PostMapping("/part")
    public Result<?> uploadPart(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("chunk") MultipartFile chunk) throws IOException {

        ChunkMeta meta = SESSIONS.get(uploadId);
        if (meta == null) throw new BusinessException(ResultCode.VALIDATE_FAILED, "上传会话不存在或已过期");
        if (chunkIndex < 0 || chunkIndex >= meta.totalChunks) throw new BusinessException(ResultCode.VALIDATE_FAILED, "分片索引越界");
        if (chunk.isEmpty()) throw new BusinessException(ResultCode.VALIDATE_FAILED, "分片内容为空");

        Path partFile = meta.tempDir.resolve(String.format("%05d", chunkIndex));
        chunk.transferTo(partFile.toFile());
        return Result.success();
    }

    /** 合并所有分片 */
    @PostMapping("/merge")
    public Result<?> merge(@RequestBody Map<String, Object> body) throws IOException {
        String uploadId = String.valueOf(body.get("uploadId"));
        String filename = String.valueOf(body.getOrDefault("filename", "file"));
        int totalChunks = Integer.parseInt(String.valueOf(body.getOrDefault("totalChunks", "0")));

        ChunkMeta meta = SESSIONS.remove(uploadId);
        if (meta == null) throw new BusinessException(ResultCode.VALIDATE_FAILED, "上传会话不存在或已过期");

        // 校验所有分片存在
        for (int i = 0; i < totalChunks; i++) {
            Path part = meta.tempDir.resolve(String.format("%05d", i));
            if (!Files.exists(part)) throw new BusinessException(ResultCode.VALIDATE_FAILED, "分片 " + i + " 缺失，请重新上传");
        }

        String ext = extractExtension(filename);
        String newFilename = IdUtils.genId("FILE") + ext;
        String safeModule = meta.module.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safeModule.isBlank()) safeModule = "common";
        java.time.LocalDate today = java.time.LocalDate.now();
        String datePath = today.getYear() + "/" + String.format("%02d", today.getMonthValue()) + "/" + String.format("%02d", today.getDayOfMonth());
        Path targetDir = Paths.get(FileUtils.getProjectRootPath(), "data", "uploads", safeModule, today.getYear() + "",
                String.format("%02d", today.getMonthValue()), String.format("%02d", today.getDayOfMonth()));
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(newFilename);

        // 顺序合并
        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            for (int i = 0; i < totalChunks; i++) {
                Path part = meta.tempDir.resolve(String.format("%05d", i));
                Files.copy(part, out);
            }
        }

        // 清理临时目录
        deleteDir(meta.tempDir);

        String prefix = urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
        String url = prefix + safeModule + "/" + datePath + "/" + newFilename;
        log.info("分片合并成功: uploadId={}, url={}", uploadId, url);

        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return Result.success(data);
    }

    private static void deleteDir(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static class ChunkMeta {
        String uploadId;
        String filename;
        int totalChunks;
        String module;
        Path tempDir;
        long createdAt;
    }
}
