package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.entity.MediaFile;
import com.campus.trend.campus_pulse.r2.R2Properties;
import com.campus.trend.campus_pulse.r2.R2Service;
import com.campus.trend.campus_pulse.service.MediaFileService;
import com.campus.trend.campus_pulse.service.MediaStorageAdminService;
import com.campus.trend.campus_pulse.service.PostMediaService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/admin/media", "/api/admin/media"})
public class MediaAdminController {

    private final MediaFileService mediaFileService;
    private final PostMediaService postMediaService;
    private final R2Service r2Service;
    private final R2Properties r2Properties;
    private final MediaStorageAdminService mediaStorageAdminService;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", r2Properties.isEnabled() ? "up" : "down");
        data.put("service", "cloudflare-r2");
        data.put("version", "direct-upload");
        data.put("timestamp", Instant.now().toString());
        return Result.success(data);
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        Map<String, Object> stats = mediaFileService.buildStats();
        long fileTotalSizeBytes = toLong(stats.get("fileTotalSizeBytes"));

        Map<String, Object> disk = new LinkedHashMap<>();
        disk.put("rootDir", "r2://" + r2Properties.getBucket());
        disk.put("totalBytes", fileTotalSizeBytes);
        disk.put("usedBytes", fileTotalSizeBytes);
        disk.put("freeBytes", 0L);
        disk.put("usedPercent", fileTotalSizeBytes > 0 ? 100.0d : 0.0d);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serviceStatus", r2Properties.isEnabled() ? "UP" : "DOWN");
        data.put("currentUploadingTasks", 0);
        data.put("uploadSuccessCount", toLong(stats.get("uploadSuccessCount")));
        data.put("uploadFailureCount", 0);
        data.put("imageCount", toLong(stats.get("imageCount")));
        data.put("videoCount", toLong(stats.get("videoCount")));
        data.put("fileTotalSizeBytes", fileTotalSizeBytes);
        data.put("disk", disk);
        data.put("qps", 0.0d);
        data.put("averageResponseMs", 0.0d);
        data.put("errorRate", 0.0d);
        data.put("activeConnections", 0);
        data.put("chunkInProgressCount", 0);
        data.put("recentUploads", mapRecentUploads(mediaFileService.listRecent(12)));
        data.put("runtimeConfig", Map.of(
                "enabled", r2Properties.isEnabled(),
                "endpoint", r2Properties.getEndpoint(),
                "region", r2Properties.getRegion(),
                "bucket", r2Properties.getBucket(),
                "publicBaseUrl", r2Properties.getPublicBaseUrl(),
                "singlePutThresholdMb", r2Properties.getSinglePutThresholdMb(),
                "partSizeMb", r2Properties.getPartSizeMb(),
                "cacheControl", r2Properties.getCacheControl()));
        return Result.success(data);
    }

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(mediaStorageAdminService.getConfigSnapshot());
    }

    @PostMapping("/config")
    public Result<Map<String, Object>> saveConfig(@RequestBody Map<String, Object> body) {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(mediaStorageAdminService.saveConfig(body));
    }

    @PostMapping("/maintenance/public-check")
    public Result<Map<String, Object>> checkPublicAccess(@RequestBody(required = false) Map<String, Object> body) {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(mediaStorageAdminService.checkPublicAccess(extractPublicBaseUrl(body)));
    }

    @PostMapping("/maintenance/rebuild-access-urls")
    public Result<Map<String, Object>> rebuildAccessUrls(@RequestBody(required = false) Map<String, Object> body) {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(mediaStorageAdminService.rebuildAccessUrls(extractPublicBaseUrl(body)));
    }

    @GetMapping("/system")
    public Result<Map<String, Object>> system() {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(buildSystemStatus());
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(mediaFileService.buildStats());
    }

    @GetMapping("/uploads")
    public Result<Map<String, Object>> listUploads(@RequestParam Map<String, String> params) {
        return listFiles(params);
    }

    @GetMapping("/files")
    public Result<Map<String, Object>> listFiles(@RequestParam Map<String, String> params) {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        int pageNum = parseInt(params.get("page"), 1);
        int pageSize = parseInt(params.get("pageSize"), parseInt(params.get("size"), 20));
        Page<MediaFile> page = mediaFileService.pageFiles(
                pageNum,
                Math.min(pageSize, 100),
                params.get("keyword"),
                params.get("mediaType"),
                params.get("bizType"),
                params.containsKey("status") ? parseInt(params.get("status"), 1) : 1);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", page.getRecords());
        data.put("current", page.getCurrent());
        data.put("size", page.getSize());
        data.put("total", page.getTotal());
        return Result.success(data);
    }

    @GetMapping("/files/{id}")
    public Result<MediaFile> fileDetail(@PathVariable String id) {
        Result<MediaFile> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        MediaFile file = mediaFileService.getById(id);
        if (file == null) {
            return Result.error(ResultCode.FAILED, "媒体文件不存在");
        }
        return Result.success(file);
    }

    @DeleteMapping("/files/{id}")
    public Result<Map<String, Object>> deleteFile(@PathVariable String id) {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        MediaFile file = mediaFileService.getById(id);
        if (file == null) {
            return Result.error(ResultCode.FAILED, "媒体文件不存在");
        }
        r2Service.delete(file.getFileKey());
        mediaFileService.softDelete(id);
        try {
            postMediaService.deleteByFileId(id);
        } catch (Exception e) {
            log.warn("删除媒体时清理帖子引用失败 fileId={}, err={}", id, e.getMessage());
        }
        return Result.success(Map.of("id", id, "fileKey", file.getFileKey(), "deleted", true));
    }

    @PostMapping("/files/batch-delete")
    public Result<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body) {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        Object idsObj = body != null ? body.get("ids") : null;
        if (!(idsObj instanceof Collection<?> ids)) {
            return Result.error(ResultCode.PARAM_ERROR, "ids 不能为空");
        }
        int affected = 0;
        for (Object item : ids) {
            if (item == null) {
                continue;
            }
            String id = String.valueOf(item);
            MediaFile file = mediaFileService.getById(id);
            if (file == null) {
                continue;
            }
            r2Service.delete(file.getFileKey());
            if (mediaFileService.softDelete(id)) {
                affected++;
            }
            try {
                postMediaService.deleteByFileId(id);
            } catch (Exception e) {
                log.warn("批量删除媒体时清理帖子引用失败 fileId={}, err={}", id, e.getMessage());
            }
        }
        return Result.success(Map.of("deletedCount", affected));
    }

    private List<Map<String, Object>> mapRecentUploads(List<MediaFile> items) {
        List<Map<String, Object>> rows = new ArrayList<>(items.size());
        for (MediaFile item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", item.getId());
            row.put("fileId", item.getId());
            row.put("bizType", item.getBizType());
            row.put("bizId", item.getBizId());
            row.put("mediaType", item.getMediaType());
            row.put("originalName", item.getOriginalName());
            row.put("sizeBytes", item.getSizeBytes());
            row.put("status", item.getStatus() != null && item.getStatus() == 1 ? "success" : "deleted");
            row.put("createdAt", item.getCreateTime());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> buildSystemStatus() {
        Runtime runtime = Runtime.getRuntime();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        java.lang.management.ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        long uptimeSeconds = runtimeMXBean.getUptime() / 1000;
        long processMemoryBytes = runtime.totalMemory() - runtime.freeMemory();

        double processCpuPercent = 0.0d;
        double systemCpuPercent = 0.0d;
        long totalPhysicalMemory = 0L;
        long freePhysicalMemory = 0L;
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            processCpuPercent = Math.max(0.0d, osBean.getProcessCpuLoad() * 100.0d);
            systemCpuPercent = Math.max(0.0d, osBean.getSystemCpuLoad() * 100.0d);
            totalPhysicalMemory = osBean.getTotalPhysicalMemorySize();
            freePhysicalMemory = osBean.getFreePhysicalMemorySize();
        } catch (Exception ignored) {
        }

        long gcCount = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = bean.getCollectionCount();
            if (count > 0) {
                gcCount += count;
            }
        }

        long usedPhysicalMemory = Math.max(0L, totalPhysicalMemory - freePhysicalMemory);
        double memoryPercent = totalPhysicalMemory > 0
                ? usedPhysicalMemory * 100.0d / totalPhysicalMemory
                : 0.0d;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hostname", resolveHostname());
        data.put("uptimeSeconds", uptimeSeconds);
        data.put("processCpuPercent", processCpuPercent);
        data.put("systemCpuPercent", systemCpuPercent);
        data.put("processMemoryBytes", processMemoryBytes);
        data.put("systemMemoryBytes", totalPhysicalMemory);
        data.put("systemMemoryUsedBytes", usedPhysicalMemory);
        data.put("systemMemoryPercent", memoryPercent);
        data.put("goroutines", threadMXBean.getThreadCount());
        data.put("gcCount", gcCount);
        data.put("startedAt", Instant.ofEpochMilli(runtimeMXBean.getStartTime()).toString());
        return data;
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private int parseInt(String raw, int defaultValue) {
        try {
            return raw == null ? defaultValue : Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String extractPublicBaseUrl(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object value = body.get("publicBaseUrl");
        return value == null ? null : String.valueOf(value);
    }

    private <T> Result<T> requireAdmin() {
        if (!PermissionUtils.isAdmin()) {
            return Result.error(ResultCode.NO_PERMISSION, "仅管理员可访问媒体管理");
        }
        return null;
    }
}
