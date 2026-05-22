package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.config.properties.LogManagementProperties;
import com.campus.trend.campus_pulse.dto.response.LogBackupResp;
import com.campus.trend.campus_pulse.dto.response.LogCleanupResp;
import com.campus.trend.campus_pulse.dto.response.LogContentResp;
import com.campus.trend.campus_pulse.dto.response.LogFileSummaryResp;
import com.campus.trend.campus_pulse.dto.response.LogOverviewResp;
import com.campus.trend.campus_pulse.service.LogManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogManagementServiceImpl implements LogManagementService {

    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter BACKUP_FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int FILE_SCAN_DEPTH = 6;
    private static final int BUFFER_SIZE = 8192;

    private final LogManagementProperties properties;

    @Value("${logging.file.name:./log/logfile.log}")
    private String activeLogFile;

    @Value("${logging.logback.rollingpolicy.file-name-pattern:}")
    private String archivePattern;

    @Value("${logging.logback.rollingpolicy.max-file-size:}")
    private String rollingMaxFileSize;

    @Value("${logging.logback.rollingpolicy.max-history:0}")
    private Integer rollingMaxHistory;

    @Value("${logging.logback.rollingpolicy.total-size-cap:}")
    private String rollingTotalSizeCap;

    @Value("${logging.logback.rollingpolicy.clean-history-on-start:false}")
    private Boolean cleanHistoryOnStart;

    @Override
    public LogOverviewResp getOverview() {
        Path baseDir = resolveBaseDir();
        Path backupDir = resolveBackupDir();
        Path activePath = resolveActiveLogPath();
        List<ManagedLogFile> files = scanManagedFiles(null, false);

        LogOverviewResp resp = new LogOverviewResp();
        resp.setBaseDir(baseDir.toString());
        resp.setBackupDir(backupDir.toString());
        resp.setActiveFileName(activePath.getFileName().toString());
        resp.setActiveFilePath(activePath.toString());
        resp.setActiveFileAccessible(Files.isRegularFile(activePath));
        resp.setArchivePattern(archivePattern);
        resp.setRollingMaxFileSize(rollingMaxFileSize);
        resp.setRollingMaxHistory(rollingMaxHistory);
        resp.setRollingTotalSizeCap(rollingTotalSizeCap);
        resp.setCleanHistoryOnStart(Boolean.TRUE.equals(cleanHistoryOnStart));
        resp.setAutoBackupEnabled(properties.isAutoBackupEnabled());
        resp.setAutoBackupCron(properties.getAutoBackupCron());
        resp.setCleanupCron(properties.getCleanupCron());
        resp.setBackupRetentionDays(Math.max(properties.getBackupRetentionDays(), 0));
        resp.setMaxBackupFiles(Math.max(properties.getMaxBackupFiles(), 0));
        resp.setMaxBackupTotalSizeBytes(toSizeBytes(properties.getMaxBackupTotalSizeMb()));
        resp.setPreviewMaxLines(clamp(properties.getPreviewMaxLines(), 50, 2000));
        resp.setPreviewMaxBytes(clamp(properties.getPreviewMaxBytes(), 4096, 1024 * 1024));
        resp.setListLimit(clamp(properties.getListLimit(), 20, 1000));
        resp.setTotalFiles(files.size());
        resp.setTotalSizeBytes(files.stream().mapToLong(file -> file.sizeBytes).sum());
        resp.setArchiveFiles(files.stream().filter(file -> "archive".equals(file.type)).count());
        resp.setBackupFiles(files.stream().filter(file -> "backup".equals(file.type)).count());
        resp.setCompressedFiles(files.stream().filter(file -> file.compressed).count());
        resp.setActiveSizeBytes(safeFileSize(activePath));
        return resp;
    }

    @Override
    public List<LogFileSummaryResp> listFiles(String keyword) {
        return scanManagedFiles(keyword, true).stream()
                .map(this::toSummaryResp)
                .collect(Collectors.toList());
    }

    @Override
    public LogContentResp readContent(String relativePath, String mode, Integer lines, Integer maxBytes) {
        Path path = resolveManagedFile(relativePath);
        ManagedLogFile logFile = buildManagedFile(path);

        int lineLimit = clamp(lines == null ? properties.getDefaultPreviewLines() : lines, 20, clamp(properties.getPreviewMaxLines(), 50, 2000));
        int byteLimit = clamp(maxBytes == null ? properties.getPreviewMaxBytes() : maxBytes, 2048, clamp(properties.getPreviewMaxBytes(), 4096, 1024 * 1024));
        String appliedMode = normalizeMode(mode, logFile.compressed);

        PreviewPayload payload = logFile.compressed
                ? readCompressedPreview(path, lineLimit, byteLimit)
                : ("head".equals(appliedMode) ? readHeadPreview(path, lineLimit, byteLimit) : readTailPreview(path, lineLimit, byteLimit));

        LogContentResp resp = new LogContentResp();
        resp.setRelativePath(logFile.publicPath);
        resp.setFileName(logFile.fileName);
        resp.setMode(appliedMode);
        resp.setLineCount(payload.lineCount);
        resp.setMaxLines(lineLimit);
        resp.setMaxBytes(byteLimit);
        resp.setTruncated(payload.truncated);
        resp.setCompressed(logFile.compressed);
        resp.setSizeBytes(logFile.sizeBytes);
        resp.setModifiedTime(logFile.modifiedTime);
        resp.setContent(payload.content);
        return resp;
    }

    @Override
    public LogBackupResp createBackupSnapshot() {
        Path activePath = resolveActiveLogPath();
        if (!Files.isRegularFile(activePath)) {
            throw new IllegalArgumentException("当前日志文件不存在，无法创建备份");
        }

        LocalDateTime now = LocalDateTime.now();
        String backupName = stripCompressionSuffix(activePath.getFileName().toString())
                + "-" + BACKUP_FILE_TIME_FORMATTER.format(now) + ".gz";
        Path targetDir = resolveBackupDir()
                .resolve(String.valueOf(now.getYear()))
                .resolve(String.format(Locale.ROOT, "%02d", now.getMonthValue()));

        try {
            Files.createDirectories(targetDir);
            Path backupPath = targetDir.resolve(backupName);
            try (InputStream in = Files.newInputStream(activePath, StandardOpenOption.READ);
                 GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(backupPath, StandardOpenOption.CREATE_NEW))) {
                transfer(in, out);
            }

            ManagedLogFile file = buildManagedFile(backupPath);
            LogBackupResp resp = new LogBackupResp();
            resp.setRelativePath(file.publicPath);
            resp.setFileName(file.fileName);
            resp.setSizeBytes(file.sizeBytes);
            resp.setCreatedTime(file.modifiedTime);
            resp.setCompressed(true);
            log.info("已创建日志备份: {}", backupPath);
            return resp;
        } catch (IOException e) {
            log.error("创建日志备份失败: {}", e.getMessage(), e);
            throw new IllegalStateException("创建日志备份失败，请稍后重试");
        }
    }

    @Override
    public LogCleanupResp cleanupExpiredFiles() {
        List<ManagedLogFile> candidates = scanManagedFiles(null, false).stream()
                .filter(file -> file.deletable)
                .filter(file -> "archive".equals(file.type) || "backup".equals(file.type))
                .sorted(Comparator.comparingLong(ManagedLogFile::getModifiedEpochMilli).reversed())
                .collect(Collectors.toList());

        boolean retentionEnabled = properties.getBackupRetentionDays() > 0;
        Instant cutoff = Instant.now().minusSeconds(Math.max(properties.getBackupRetentionDays(), 0) * 86400L);
        long sizeCapBytes = toSizeBytes(Math.max(properties.getMaxBackupTotalSizeMb(), 0L));
        int fileCap = Math.max(properties.getMaxBackupFiles(), 0);

        long deletedFiles = 0L;
        long deletedBytes = 0L;
        List<ManagedLogFile> freshFiles = new ArrayList<>();

        for (ManagedLogFile candidate : candidates) {
            if (retentionEnabled && candidate.modifiedAt.isBefore(cutoff)) {
                if (deleteQuietly(candidate.path)) {
                    deletedFiles++;
                    deletedBytes += candidate.sizeBytes;
                }
                continue;
            }
            freshFiles.add(candidate);
        }

        long retainedSize = 0L;
        int retainedCount = 0;
        for (ManagedLogFile candidate : freshFiles) {
            boolean overCount = fileCap > 0 && retainedCount >= fileCap;
            boolean overSize = sizeCapBytes > 0 && retainedSize + candidate.sizeBytes > sizeCapBytes && retainedCount > 0;
            if (overCount || overSize) {
                if (deleteQuietly(candidate.path)) {
                    deletedFiles++;
                    deletedBytes += candidate.sizeBytes;
                }
                continue;
            }
            retainedCount++;
            retainedSize += candidate.sizeBytes;
        }

        LogCleanupResp resp = new LogCleanupResp();
        resp.setScannedFiles(candidates.size());
        resp.setKeptFiles(retainedCount);
        resp.setDeletedFiles(deletedFiles);
        resp.setDeletedBytes(deletedBytes);
        log.info("日志清理完成，扫描 {} 个候选文件，删除 {} 个文件，释放 {} 字节", candidates.size(), deletedFiles, deletedBytes);
        return resp;
    }

    @Override
    public void deleteFile(String relativePath) {
        Path path = resolveManagedFile(relativePath);
        ManagedLogFile file = buildManagedFile(path);
        if (!file.deletable) {
            throw new IllegalArgumentException("当前日志文件不允许删除");
        }

        try {
            Files.deleteIfExists(path);
            log.info("已删除日志文件: {}", path);
        } catch (IOException e) {
            log.error("删除日志文件失败: {}", e.getMessage(), e);
            throw new IllegalStateException("删除日志文件失败，请稍后重试");
        }
    }

    @Override
    public Path resolveFileForDownload(String relativePath) {
        return resolveManagedFile(relativePath);
    }

    private List<ManagedLogFile> scanManagedFiles(String keyword, boolean applyLimit) {
        Path activePath = resolveActiveLogPath();
        Path activeParent = Objects.requireNonNullElse(activePath.getParent(), resolveBaseDir());

        Map<Path, ManagedLogFile> files = new LinkedHashMap<>();
        collectFiles(files, resolveBaseDir(), keyword);
        collectFiles(files, resolveBackupDir(), keyword);
        if (!activePath.startsWith(resolveBaseDir()) && !activePath.startsWith(resolveBackupDir())) {
            collectFiles(files, activeParent, keyword);
        }
        if (Files.isRegularFile(activePath) && matchesKeyword(activePath, keyword)) {
            files.put(activePath, buildManagedFile(activePath));
        }

        Stream<ManagedLogFile> stream = files.values().stream()
                .sorted(Comparator
                        .comparing(ManagedLogFile::isActive).reversed()
                        .thenComparing(ManagedLogFile::getModifiedEpochMilli, Comparator.reverseOrder())
                        .thenComparing(ManagedLogFile::getFileName));
        if (applyLimit) {
            stream = stream.limit(clamp(properties.getListLimit(), 20, 1000));
        }
        return stream.collect(Collectors.toList());
    }

    private void collectFiles(Map<Path, ManagedLogFile> files, Path root, String keyword) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root, FILE_SCAN_DEPTH)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isAllowedLogFile)
                    .filter(path -> matchesKeyword(path, keyword))
                    .forEach(path -> files.put(path.toAbsolutePath().normalize(), buildManagedFile(path.toAbsolutePath().normalize())));
        } catch (IOException e) {
            log.warn("扫描日志目录失败: root={}, err={}", root, e.getMessage());
        }
    }

    private ManagedLogFile buildManagedFile(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        boolean active = normalized.equals(resolveActiveLogPath());
        boolean compressed = isCompressed(normalized);
        String type = classify(normalized, active);
        boolean deletable = !active && ("archive".equals(type) || "backup".equals(type));
        FileTime modifiedTime = safeLastModifiedTime(normalized);

        ManagedLogFile file = new ManagedLogFile();
        file.path = normalized;
        file.publicPath = buildPublicPath(normalized);
        file.fileName = normalized.getFileName().toString();
        file.type = type;
        file.active = active;
        file.compressed = compressed;
        file.previewTailSupported = !compressed;
        file.downloadable = true;
        file.deletable = deletable;
        file.sizeBytes = safeFileSize(normalized);
        file.modifiedAt = modifiedTime.toInstant();
        file.modifiedEpochMilli = modifiedTime.toMillis();
        file.modifiedTime = formatInstant(modifiedTime.toInstant());
        return file;
    }

    private LogFileSummaryResp toSummaryResp(ManagedLogFile file) {
        LogFileSummaryResp resp = new LogFileSummaryResp();
        resp.setRelativePath(file.publicPath);
        resp.setFileName(file.fileName);
        resp.setType(file.type);
        resp.setActive(file.active);
        resp.setCompressed(file.compressed);
        resp.setPreviewTailSupported(file.previewTailSupported);
        resp.setDownloadable(file.downloadable);
        resp.setDeletable(file.deletable);
        resp.setSizeBytes(file.sizeBytes);
        resp.setModifiedTime(file.modifiedTime);
        return resp;
    }

    private PreviewPayload readHeadPreview(Path path, int maxLines, int maxBytes) {
        try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
            byte[] bytes = readAtMost(in, maxBytes + 1);
            boolean truncatedByBytes = bytes.length > maxBytes;
            String content = bytesToString(truncatedByBytes ? trimBytes(bytes, maxBytes) : bytes);
            return slicePreview(content, maxLines, truncatedByBytes, false);
        } catch (IOException e) {
            log.error("读取日志头部失败: {}", e.getMessage(), e);
            throw new IllegalStateException("读取日志内容失败，请稍后重试");
        }
    }

    private PreviewPayload readTailPreview(Path path, int maxLines, int maxBytes) {
        long fileSize = safeFileSize(path);
        int bytesToRead = (int) Math.min(fileSize, maxBytes);
        if (bytesToRead <= 0) {
            return new PreviewPayload("", 0, false);
        }

        byte[] data = new byte[bytesToRead];
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long start = Math.max(0L, fileSize - bytesToRead);
            file.seek(start);
            file.readFully(data);
            boolean truncatedByBytes = start > 0;
            String content = bytesToString(data);
            if (truncatedByBytes) {
                int firstBreak = content.indexOf('\n');
                if (firstBreak >= 0 && firstBreak + 1 < content.length()) {
                    content = content.substring(firstBreak + 1);
                }
            }
            return slicePreview(content, maxLines, truncatedByBytes, true);
        } catch (IOException e) {
            log.error("读取日志尾部失败: {}", e.getMessage(), e);
            throw new IllegalStateException("读取日志内容失败，请稍后重试");
        }
    }

    private PreviewPayload readCompressedPreview(Path path, int maxLines, int maxBytes) {
        try (InputStream raw = Files.newInputStream(path, StandardOpenOption.READ);
             GZIPInputStream gzip = new GZIPInputStream(raw)) {
            byte[] bytes = readAtMost(gzip, maxBytes + 1);
            boolean truncatedByBytes = bytes.length > maxBytes;
            String content = bytesToString(truncatedByBytes ? trimBytes(bytes, maxBytes) : bytes);
            return slicePreview(content, maxLines, truncatedByBytes, false);
        } catch (IOException e) {
            log.error("读取压缩日志失败: {}", e.getMessage(), e);
            throw new IllegalStateException("读取压缩日志内容失败，请稍后重试");
        }
    }

    private PreviewPayload slicePreview(String rawContent, int maxLines, boolean truncatedByBytes, boolean tailMode) {
        List<String> lines = splitLines(rawContent);
        boolean truncatedByLines = lines.size() > maxLines;
        List<String> visibleLines;
        if (truncatedByLines) {
            visibleLines = tailMode ? lines.subList(lines.size() - maxLines, lines.size()) : lines.subList(0, maxLines);
        } else {
            visibleLines = lines;
        }

        return new PreviewPayload(String.join("\n", visibleLines), visibleLines.size(), truncatedByBytes || truncatedByLines);
    }

    private Path resolveManagedFile(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("缺少日志文件标识");
        }
        String normalized = relativePath.replace('\\', '/');
        int slashIndex = normalized.indexOf('/');
        if (slashIndex < 0 || slashIndex == normalized.length() - 1) {
            throw new IllegalArgumentException("日志文件标识格式不正确");
        }

        String scope = normalized.substring(0, slashIndex);
        String relative = normalized.substring(slashIndex + 1);
        Path root;
        switch (scope) {
            case "base" -> root = resolveBaseDir();
            case "backup" -> root = resolveBackupDir();
            case "active" -> root = Objects.requireNonNullElse(resolveActiveLogPath().getParent(), resolveBaseDir());
            default -> throw new IllegalArgumentException("未知的日志文件类型");
        }

        Path resolved = root.resolve(relative).normalize().toAbsolutePath();
        if (!resolved.startsWith(root.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("非法的日志文件路径");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("日志文件不存在或不可访问");
        }
        if (!isAllowedLogFile(resolved) && !resolved.equals(resolveActiveLogPath())) {
            throw new IllegalArgumentException("当前文件不在受控日志范围内");
        }
        return resolved;
    }

    private String buildPublicPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path backupDir = resolveBackupDir();
        Path baseDir = resolveBaseDir();
        Path activeParent = Objects.requireNonNullElse(resolveActiveLogPath().getParent(), baseDir);

        if (normalized.startsWith(backupDir)) {
            return "backup/" + toUnixPath(backupDir.relativize(normalized));
        }
        if (normalized.startsWith(baseDir)) {
            return "base/" + toUnixPath(baseDir.relativize(normalized));
        }
        return "active/" + toUnixPath(activeParent.relativize(normalized));
    }

    private boolean isAllowedLogFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        List<String> extensions = properties.getAllowedExtensions();
        if (extensions == null || extensions.isEmpty()) {
            return fileName.endsWith(".log");
        }
        return extensions.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .anyMatch(fileName::endsWith);
    }

    private boolean matchesKeyword(Path path, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String term = keyword.trim().toLowerCase(Locale.ROOT);
        String candidate = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return candidate.contains(term) || path.toString().toLowerCase(Locale.ROOT).contains(term);
    }

    private String classify(Path path, boolean active) {
        if (active) {
            return "active";
        }
        if (path.startsWith(resolveBackupDir())) {
            return "backup";
        }
        Path parent = path.getParent();
        if (parent != null && parent.toString().toLowerCase(Locale.ROOT).contains("archive")) {
            return "archive";
        }
        if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz")) {
            return "archive";
        }
        return "log";
    }

    private boolean isCompressed(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz");
    }

    private String normalizeMode(String mode, boolean compressed) {
        if (compressed) {
            return "head";
        }
        return "head".equalsIgnoreCase(mode) ? "head" : "tail";
    }

    private List<String> splitLines(String content) {
        if (!StringUtils.hasText(content)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(content.split("\\R")));
    }

    private byte[] readAtMost(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(limit, BUFFER_SIZE * 4));
        byte[] chunk = new byte[BUFFER_SIZE];
        int total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            int remain = limit - total;
            if (remain <= 0) {
                break;
            }
            int write = Math.min(read, remain);
            buffer.write(chunk, 0, write);
            total += write;
            if (total >= limit) {
                break;
            }
        }
        return buffer.toByteArray();
    }

    private void transfer(InputStream in, GZIPOutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.finish();
    }

    private byte[] trimBytes(byte[] data, int maxBytes) {
        byte[] trimmed = new byte[maxBytes];
        System.arraycopy(data, 0, trimmed, 0, maxBytes);
        return trimmed;
    }

    private String bytesToString(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }

    private boolean deleteQuietly(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除日志文件失败: path={}, err={}", path, e.getMessage());
            return false;
        }
    }

    private FileTime safeLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.fromMillis(0L);
        }
    }

    private long safeFileSize(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    private Path resolveBaseDir() {
        return resolveAbsolutePath(properties.getBaseDir());
    }

    private Path resolveBackupDir() {
        return resolveAbsolutePath(properties.getBackupDir());
    }

    private Path resolveActiveLogPath() {
        return resolveAbsolutePath(activeLogFile);
    }

    private Path resolveAbsolutePath(String rawPath) {
        Path path = Paths.get(StringUtils.hasText(rawPath) ? rawPath : "./log");
        if (!path.isAbsolute()) {
            path = Paths.get("").toAbsolutePath().resolve(path);
        }
        return path.normalize();
    }

    private String toUnixPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private long toSizeBytes(long sizeMb) {
        return Math.max(sizeMb, 0L) * 1024L * 1024L;
    }

    private String formatInstant(Instant instant) {
        return DISPLAY_TIME_FORMATTER.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }

    private String stripCompressionSuffix(String fileName) {
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".gz")) {
            return fileName.substring(0, fileName.length() - 3);
        }
        return fileName;
    }

    private static final class ManagedLogFile {
        private Path path;
        private String publicPath;
        private String fileName;
        private String type;
        private boolean active;
        private boolean compressed;
        private boolean previewTailSupported;
        private boolean downloadable;
        private boolean deletable;
        private long sizeBytes;
        private Instant modifiedAt;
        private long modifiedEpochMilli;
        private String modifiedTime;

        private boolean isActive() {
            return active;
        }

        private String getFileName() {
            return fileName;
        }

        private Long getModifiedEpochMilli() {
            return modifiedEpochMilli;
        }
    }

    private static final class PreviewPayload {
        private final String content;
        private final int lineCount;
        private final boolean truncated;

        private PreviewPayload(String content, int lineCount, boolean truncated) {
            this.content = content;
            this.lineCount = lineCount;
            this.truncated = truncated;
        }
    }
}
