package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.entity.MediaFile;
import com.campus.trend.campus_pulse.r2.R2Properties;
import com.campus.trend.campus_pulse.r2.R2Service;
import com.campus.trend.campus_pulse.service.MediaFileService;
import com.campus.trend.campus_pulse.service.MediaStorageAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaStorageAdminServiceImpl implements MediaStorageAdminService {

    private static final String KEY_ENABLED = "R2_ENABLED";
    private static final String KEY_ENDPOINT = "R2_ENDPOINT";
    private static final String KEY_REGION = "R2_REGION";
    private static final String KEY_BUCKET = "R2_BUCKET";
    private static final String KEY_PUBLIC_BASE_URL = "R2_PUBLIC_BASE_URL";
    private static final String KEY_PRESIGN_TTL = "R2_PRESIGN_TTL";
    private static final String KEY_SINGLE_PUT_THRESHOLD = "R2_SINGLE_PUT_THRESHOLD_MB";
    private static final String KEY_PART_SIZE = "R2_PART_SIZE_MB";
    private static final String KEY_MAX_IMAGE = "R2_MAX_IMAGE_MB";
    private static final String KEY_MAX_VIDEO = "R2_MAX_VIDEO_MB";
    private static final String KEY_ALLOWED_IMAGE_EXTENSIONS = "R2_ALLOWED_IMAGE_EXTENSIONS";
    private static final String KEY_ALLOWED_VIDEO_EXTENSIONS = "R2_ALLOWED_VIDEO_EXTENSIONS";
    private static final String KEY_CACHE_CONTROL = "R2_CACHE_CONTROL";
    private static final List<String> MANAGED_KEYS = List.of(
            KEY_ENABLED,
            KEY_ENDPOINT,
            KEY_REGION,
            KEY_BUCKET,
            KEY_PUBLIC_BASE_URL,
            KEY_PRESIGN_TTL,
            KEY_SINGLE_PUT_THRESHOLD,
            KEY_PART_SIZE,
            KEY_MAX_IMAGE,
            KEY_MAX_VIDEO,
            KEY_ALLOWED_IMAGE_EXTENSIONS,
            KEY_ALLOWED_VIDEO_EXTENSIONS,
            KEY_CACHE_CONTROL);
    private static final String RECOMMENDED_CACHE_CONTROL = "public, max-age=3600, stale-while-revalidate=60";

    private final MediaFileService mediaFileService;
    private final R2Properties r2Properties;
    private final R2Service r2Service;

    @Override
    public Map<String, Object> getConfigSnapshot() {
        Map<String, String> fileValues = readManagedFileValues();
        Map<String, Object> runtimeConfig = buildRuntimeConfig();
        Map<String, Object> savedConfig = overlaySavedConfig(fileValues, runtimeConfig);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configFile", resolveConfigFile().toString());
        data.put("credentialsConfigured", credentialsConfigured());
        data.put("savedConfig", savedConfig);
        data.put("runtimeConfig", runtimeConfig);
        data.put("runtimeMatchesSaved", Objects.equals(savedConfig, runtimeConfig));
        data.put("recommendedCacheControl", RECOMMENDED_CACHE_CONTROL);
        data.put("corsAllowedHeaders", List.of("Content-Type", "Cache-Control", "Range"));
        data.put("restartHint", "已保存设置写入 .env.local；修改 endpoint / region / 凭证 / bucket 后请重启后端。");
        return data;
    }

    @Override
    public Map<String, Object> saveConfig(Map<String, Object> body) {
        Map<String, Object> nextConfig = buildConfigFromBody(body);
        validateConfig(nextConfig);
        writeManagedConfig(nextConfig);

        Map<String, Object> data = getConfigSnapshot();
        data.put("savedAt", LocalDateTime.now().toString());
        data.put("restartRequired", true);
        return data;
    }

    @Override
    public Map<String, Object> checkPublicAccess(String overridePublicBaseUrl) {
        String publicBaseUrl = resolveCheckBaseUrl(overridePublicBaseUrl);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("checkedAt", Instant.now().toString());
        data.put("publicBaseUrl", publicBaseUrl);

        List<String> advice = new ArrayList<>();
        Integer rootStatus = probeStatus(appendProbe(publicBaseUrl));
        data.put("baseStatus", rootStatus);
        data.put("baseReachable", rootStatus != null);
        if (rootStatus == null) {
            advice.add("公开域名无法连接，请确认 DNS / 自定义域名 / 证书配置。");
        } else if (rootStatus >= 500) {
            advice.add("公开域名返回 5xx，请检查 Cloudflare 自定义域名映射或源站异常。");
        } else if (rootStatus == 404) {
            advice.add("域名根路径返回 404 不一定异常，重点看具体对象 URL 是否可访问。");
        }

        MediaFile sample = mediaFileService.listRecent(1).stream().findFirst().orElse(null);
        if (sample == null || !StringUtils.hasText(sample.getFileKey())) {
            advice.add("当前没有最近上传文件，无法做样本对象校验。");
            data.put("ok", false);
            data.put("advice", advice);
            return data;
        }

        String sampleAccessUrl = buildPublicUrl(publicBaseUrl, sample.getFileKey());
        boolean objectExists = r2Service.head(sample.getFileKey()) != null;
        Integer sampleStatus = probeStatus(appendProbe(sampleAccessUrl));

        data.put("sampleFileId", sample.getId());
        data.put("sampleFileKey", sample.getFileKey());
        data.put("sampleAccessUrl", sampleAccessUrl);
        data.put("sampleObjectExists", objectExists);
        data.put("samplePublicStatus", sampleStatus);

        boolean ok = objectExists && sampleStatus != null && sampleStatus >= 200 && sampleStatus < 300;
        data.put("ok", ok);

        if (!objectExists) {
            advice.add("样本对象在当前 R2 bucket 中不存在，请检查 bucket 配置或对象是否已被删除。");
        }
        if (sampleStatus == null) {
            advice.add("样本对象公开地址请求失败，请确认公开域名是否正确连接到 R2 bucket。");
        } else if (sampleStatus == 404 && objectExists) {
            advice.add("样本对象在 R2 存在但公开地址返回 404，通常是 publicBaseUrl 或 Cloudflare 自定义域名映射有误。");
        } else if (sampleStatus >= 400) {
            advice.add("样本对象公开访问异常，请检查 R2 Public Bucket / 自定义域名 / 缓存规则。");
        }
        if (ok) {
            advice.add("样本对象公开可访问，当前公开域名配置正常。");
        }

        data.put("advice", advice);
        return data;
    }

    @Override
    public Map<String, Object> rebuildAccessUrls(String overridePublicBaseUrl) {
        String publicBaseUrl = resolveCheckBaseUrl(overridePublicBaseUrl);
        long scannedCount = mediaFileService.lambdaQuery()
                .eq(MediaFile::getStatus, 1)
                .count();
        long updatedCount = mediaFileService.rebuildActiveAccessUrls(publicBaseUrl);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("publicBaseUrl", publicBaseUrl);
        data.put("scannedCount", scannedCount);
        data.put("updatedCount", updatedCount);
        data.put("checkedAt", Instant.now().toString());
        data.put("message", "已按新的公开域名重建 accessUrl，bucket / endpoint 如有修改请重启后端后再做上传验证。");
        return data;
    }

    private Path resolveConfigFile() {
        return Paths.get(".env.local").toAbsolutePath().normalize();
    }

    private boolean credentialsConfigured() {
        return StringUtils.hasText(r2Properties.getAccessKeyId())
                && StringUtils.hasText(r2Properties.getSecretAccessKey());
    }

    private Map<String, Object> buildRuntimeConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", r2Properties.isEnabled());
        config.put("endpoint", defaultString(r2Properties.getEndpoint()));
        config.put("region", defaultString(r2Properties.getRegion()));
        config.put("bucket", defaultString(r2Properties.getBucket()));
        config.put("publicBaseUrl", defaultString(r2Properties.getPublicBaseUrl()));
        config.put("presignTtlSeconds", r2Properties.getPresignTtlSeconds());
        config.put("singlePutThresholdMb", r2Properties.getSinglePutThresholdMb());
        config.put("partSizeMb", r2Properties.getPartSizeMb());
        config.put("maxImageSizeMb", r2Properties.getMaxImageSizeMb());
        config.put("maxVideoSizeMb", r2Properties.getMaxVideoSizeMb());
        config.put("allowedImageExtensions", defaultString(r2Properties.getAllowedImageExtensions()));
        config.put("allowedVideoExtensions", defaultString(r2Properties.getAllowedVideoExtensions()));
        config.put("cacheControl", defaultString(r2Properties.getCacheControl()));
        return config;
    }

    private Map<String, Object> overlaySavedConfig(Map<String, String> fileValues, Map<String, Object> runtimeConfig) {
        Map<String, Object> saved = new LinkedHashMap<>(runtimeConfig);
        saved.put("enabled", parseBoolean(fileValues.get(KEY_ENABLED), (Boolean) runtimeConfig.get("enabled")));
        saved.put("endpoint", fileValues.getOrDefault(KEY_ENDPOINT, String.valueOf(runtimeConfig.get("endpoint"))));
        saved.put("region", fileValues.getOrDefault(KEY_REGION, String.valueOf(runtimeConfig.get("region"))));
        saved.put("bucket", fileValues.getOrDefault(KEY_BUCKET, String.valueOf(runtimeConfig.get("bucket"))));
        saved.put("publicBaseUrl", fileValues.getOrDefault(KEY_PUBLIC_BASE_URL, String.valueOf(runtimeConfig.get("publicBaseUrl"))));
        saved.put("presignTtlSeconds", parseLong(fileValues.get(KEY_PRESIGN_TTL), ((Number) runtimeConfig.get("presignTtlSeconds")).longValue()));
        saved.put("singlePutThresholdMb", parseInt(fileValues.get(KEY_SINGLE_PUT_THRESHOLD), ((Number) runtimeConfig.get("singlePutThresholdMb")).intValue()));
        saved.put("partSizeMb", parseInt(fileValues.get(KEY_PART_SIZE), ((Number) runtimeConfig.get("partSizeMb")).intValue()));
        saved.put("maxImageSizeMb", parseInt(fileValues.get(KEY_MAX_IMAGE), ((Number) runtimeConfig.get("maxImageSizeMb")).intValue()));
        saved.put("maxVideoSizeMb", parseInt(fileValues.get(KEY_MAX_VIDEO), ((Number) runtimeConfig.get("maxVideoSizeMb")).intValue()));
        saved.put("allowedImageExtensions", fileValues.getOrDefault(KEY_ALLOWED_IMAGE_EXTENSIONS, String.valueOf(runtimeConfig.get("allowedImageExtensions"))));
        saved.put("allowedVideoExtensions", fileValues.getOrDefault(KEY_ALLOWED_VIDEO_EXTENSIONS, String.valueOf(runtimeConfig.get("allowedVideoExtensions"))));
        saved.put("cacheControl", fileValues.getOrDefault(KEY_CACHE_CONTROL, String.valueOf(runtimeConfig.get("cacheControl"))));
        return saved;
    }

    private Map<String, Object> buildConfigFromBody(Map<String, Object> body) {
        Map<String, Object> runtimeConfig = buildRuntimeConfig();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", parseBoolean(body == null ? null : body.get("enabled"), (Boolean) runtimeConfig.get("enabled")));
        config.put("endpoint", normalizeText(body == null ? null : body.get("endpoint"), String.valueOf(runtimeConfig.get("endpoint"))));
        config.put("region", normalizeText(body == null ? null : body.get("region"), String.valueOf(runtimeConfig.get("region"))));
        config.put("bucket", normalizeText(body == null ? null : body.get("bucket"), String.valueOf(runtimeConfig.get("bucket"))));
        config.put("publicBaseUrl", normalizeBaseUrl(normalizeText(body == null ? null : body.get("publicBaseUrl"), String.valueOf(runtimeConfig.get("publicBaseUrl")))));
        config.put("presignTtlSeconds", parseLong(body == null ? null : body.get("presignTtlSeconds"), ((Number) runtimeConfig.get("presignTtlSeconds")).longValue()));
        config.put("singlePutThresholdMb", parseInt(body == null ? null : body.get("singlePutThresholdMb"), ((Number) runtimeConfig.get("singlePutThresholdMb")).intValue()));
        config.put("partSizeMb", parseInt(body == null ? null : body.get("partSizeMb"), ((Number) runtimeConfig.get("partSizeMb")).intValue()));
        config.put("maxImageSizeMb", parseInt(body == null ? null : body.get("maxImageSizeMb"), ((Number) runtimeConfig.get("maxImageSizeMb")).intValue()));
        config.put("maxVideoSizeMb", parseInt(body == null ? null : body.get("maxVideoSizeMb"), ((Number) runtimeConfig.get("maxVideoSizeMb")).intValue()));
        config.put("allowedImageExtensions", normalizeText(body == null ? null : body.get("allowedImageExtensions"), String.valueOf(runtimeConfig.get("allowedImageExtensions"))));
        config.put("allowedVideoExtensions", normalizeText(body == null ? null : body.get("allowedVideoExtensions"), String.valueOf(runtimeConfig.get("allowedVideoExtensions"))));
        config.put("cacheControl", normalizeText(body == null ? null : body.get("cacheControl"), String.valueOf(runtimeConfig.get("cacheControl"))));
        return config;
    }

    private void validateConfig(Map<String, Object> config) {
        boolean enabled = Boolean.TRUE.equals(config.get("enabled"));
        String endpoint = String.valueOf(config.get("endpoint"));
        String region = String.valueOf(config.get("region"));
        String bucket = String.valueOf(config.get("bucket"));
        String publicBaseUrl = String.valueOf(config.get("publicBaseUrl"));
        int singlePutThresholdMb = ((Number) config.get("singlePutThresholdMb")).intValue();
        int partSizeMb = ((Number) config.get("partSizeMb")).intValue();
        int maxImageSizeMb = ((Number) config.get("maxImageSizeMb")).intValue();
        int maxVideoSizeMb = ((Number) config.get("maxVideoSizeMb")).intValue();

        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(endpoint)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "R2 endpoint 不能为空");
        }
        if (!StringUtils.hasText(region)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "R2 region 不能为空");
        }
        if (!StringUtils.hasText(bucket)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "R2 bucket 不能为空");
        }
        if (!StringUtils.hasText(publicBaseUrl)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "R2 公开域名不能为空");
        }
        if (singlePutThresholdMb <= 0 || partSizeMb <= 0 || maxImageSizeMb <= 0 || maxVideoSizeMb <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "R2 大小阈值必须大于 0");
        }
    }

    private Map<String, String> readManagedFileValues() {
        Path path = resolveConfigFile();
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                ParsedLine parsed = parseLine(line);
                if (parsed == null || !MANAGED_KEYS.contains(parsed.key())) {
                    continue;
                }
                values.put(parsed.key(), parsed.value());
            }
        } catch (IOException e) {
            log.warn("读取媒体配置文件失败: file={}, err={}", path, e.getMessage());
        }
        return values;
    }

    private void writeManagedConfig(Map<String, Object> config) {
        Path path = resolveConfigFile();
        List<String> lines = new ArrayList<>();
        try {
            if (Files.exists(path)) {
                lines.addAll(Files.readAllLines(path, StandardCharsets.UTF_8));
            }
            if (lines.isEmpty()) {
                lines.add("# Managed by Media Storage Status admin page");
                lines.add("# Restart backend after changing endpoint / region / credentials / bucket");
            }

            Map<String, Integer> indexes = new LinkedHashMap<>();
            for (int i = 0; i < lines.size(); i++) {
                ParsedLine parsed = parseLine(lines.get(i));
                if (parsed != null && MANAGED_KEYS.contains(parsed.key())) {
                    indexes.put(parsed.key(), i);
                }
            }

            for (String key : MANAGED_KEYS) {
                String nextLine = key + "=" + stringifyConfigValue(key, config);
                Integer index = indexes.get(key);
                if (index == null) {
                    lines.add(nextLine);
                } else {
                    lines.set(index, nextLine);
                }
            }

            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("写入媒体配置文件失败: file={}, err={}", path, e.getMessage(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "保存 R2 配置失败，请检查 .env.local 文件权限");
        }
    }

    private String stringifyConfigValue(String key, Map<String, Object> config) {
        return switch (key) {
            case KEY_ENABLED -> String.valueOf(config.get("enabled"));
            case KEY_ENDPOINT -> String.valueOf(config.get("endpoint"));
            case KEY_REGION -> String.valueOf(config.get("region"));
            case KEY_BUCKET -> String.valueOf(config.get("bucket"));
            case KEY_PUBLIC_BASE_URL -> String.valueOf(config.get("publicBaseUrl"));
            case KEY_PRESIGN_TTL -> String.valueOf(config.get("presignTtlSeconds"));
            case KEY_SINGLE_PUT_THRESHOLD -> String.valueOf(config.get("singlePutThresholdMb"));
            case KEY_PART_SIZE -> String.valueOf(config.get("partSizeMb"));
            case KEY_MAX_IMAGE -> String.valueOf(config.get("maxImageSizeMb"));
            case KEY_MAX_VIDEO -> String.valueOf(config.get("maxVideoSizeMb"));
            case KEY_ALLOWED_IMAGE_EXTENSIONS -> String.valueOf(config.get("allowedImageExtensions"));
            case KEY_ALLOWED_VIDEO_EXTENSIONS -> String.valueOf(config.get("allowedVideoExtensions"));
            case KEY_CACHE_CONTROL -> String.valueOf(config.get("cacheControl"));
            default -> "";
        };
    }

    private String resolveCheckBaseUrl(String overridePublicBaseUrl) {
        String baseUrl = normalizeBaseUrl(overridePublicBaseUrl);
        if (StringUtils.hasText(baseUrl)) {
            return baseUrl;
        }
        Map<String, Object> savedConfig = (Map<String, Object>) getConfigSnapshot().get("savedConfig");
        String savedBaseUrl = savedConfig == null ? null : String.valueOf(savedConfig.get("publicBaseUrl"));
        if (StringUtils.hasText(savedBaseUrl)) {
            return normalizeBaseUrl(savedBaseUrl);
        }
        if (StringUtils.hasText(r2Properties.getPublicBaseUrl())) {
            return normalizeBaseUrl(r2Properties.getPublicBaseUrl());
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "请先配置 R2_PUBLIC_BASE_URL");
    }

    private Integer probeStatus(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (Exception e) {
            log.warn("公开地址探测失败: url={}, err={}", url, e.getMessage());
            return null;
        }
    }

    private String appendProbe(String url) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "probe=" + URLEncoder.encode(String.valueOf(System.currentTimeMillis()), StandardCharsets.UTF_8);
    }

    private String buildPublicUrl(String publicBaseUrl, String fileKey) {
        String normalizedBaseUrl = normalizeBaseUrl(publicBaseUrl);
        String normalizedFileKey = fileKey == null ? "" : fileKey.trim().replace('\\', '/');
        while (normalizedFileKey.startsWith("/")) {
            normalizedFileKey = normalizedFileKey.substring(1);
        }
        return normalizedBaseUrl + "/" + normalizedFileKey;
    }

    private ParsedLine parseLine(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("#")) {
            return null;
        }
        int index = line.indexOf('=');
        if (index <= 0) {
            return null;
        }
        String key = line.substring(0, index).trim();
        String value = line.substring(index + 1).trim();
        return StringUtils.hasText(key) ? new ParsedLine(key, value) : null;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String normalizeText(Object value, String fallback) {
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        String text = String.valueOf(value).trim();
        return text;
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean parseBoolean(Object raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean bool) {
            return bool;
        }
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(value) || "1".equals(value) || "yes".equals(value)) {
            return true;
        }
        if ("false".equals(value) || "0".equals(value) || "no".equals(value)) {
            return false;
        }
        return fallback;
    }

    private int parseInt(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseLong(Object raw, long fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record ParsedLine(String key, String value) {
    }
}
