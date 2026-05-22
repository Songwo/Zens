package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.request.R2AbortReq;
import com.campus.trend.campus_pulse.dto.request.R2CompleteReq;
import com.campus.trend.campus_pulse.dto.request.R2InitReq;
import com.campus.trend.campus_pulse.dto.request.R2PartEtagReq;
import com.campus.trend.campus_pulse.dto.request.R2PrecheckReq;
import com.campus.trend.campus_pulse.dto.response.R2CompleteResp;
import com.campus.trend.campus_pulse.dto.response.R2InitResp;
import com.campus.trend.campus_pulse.dto.response.R2PartUploadResp;
import com.campus.trend.campus_pulse.dto.response.R2PrecheckResp;
import com.campus.trend.campus_pulse.entity.MediaFile;
import com.campus.trend.campus_pulse.r2.R2Properties;
import com.campus.trend.campus_pulse.r2.R2Service;
import com.campus.trend.campus_pulse.service.MediaFileService;
import com.campus.trend.campus_pulse.utils.IdUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Tag(name = "R2 上传", description = "浏览器直传 Cloudflare R2")
@RestController
@RequestMapping("/common/upload/r2")
@RequiredArgsConstructor
public class R2UploadController {

    private static final String MODE_SINGLE = "single";
    private static final String MODE_MULTIPART = "multipart";
    private static final String MODE_HIT = "hit";
    private static final int MAX_MULTIPART_PARTS = 10_000;
    private static final int HEAD_VERIFY_MAX_ATTEMPTS = 6;
    private static final long HEAD_VERIFY_RETRY_INTERVAL_MS = 300L;

    private final R2Properties r2Properties;
    private final R2Service r2Service;
    private final MediaFileService mediaFileService;

    @PostMapping("/precheck")
    public Result<R2PrecheckResp> precheck(@Valid @RequestBody R2PrecheckReq req) {
        requireR2Enabled();
        MediaFile hit = mediaFileService.findActiveBySha256(req.getSha256());
        if (hit == null || hit.getSizeBytes() == null || !hit.getSizeBytes().equals(req.getSizeBytes())) {
            return Result.success(new R2PrecheckResp(false, null, null));
        }
        return Result.success(new R2PrecheckResp(true, hit.getAccessUrl(), hit.getId()));
    }

    @PostMapping("/init")
    public Result<R2InitResp> init(@Valid @RequestBody R2InitReq req) {
        requireCurrentUserId();
        requireR2Enabled();

        String mediaType = normalizeMediaType(req.getMediaType());
        String originalName = req.getOriginalName().trim();
        String mimeType = normalizeMimeType(req.getMimeType(), mediaType);
        String ext = resolveExtension(originalName, mimeType);
        validateFile(req.getSizeBytes(), mediaType, ext);

        if (StringUtils.hasText(req.getSha256())) {
            MediaFile hit = mediaFileService.findActiveBySha256(req.getSha256());
            if (hit != null && hit.getSizeBytes() != null && hit.getSizeBytes().equals(req.getSizeBytes())) {
                R2InitResp resp = new R2InitResp();
                resp.setMode(MODE_HIT);
                resp.setAccessUrl(hit.getAccessUrl());
                resp.setFileId(hit.getId());
                return Result.success(resp);
            }
        }

        String fileKey = r2Service.genFileKey(mediaType, ext);
        String accessUrl = r2Service.buildAccessUrl(fileKey);
        long thresholdBytes = toBytes(r2Properties.getSinglePutThresholdMb());

        if (req.getSizeBytes() <= thresholdBytes) {
            return Result.success(new R2InitResp(
                    MODE_SINGLE,
                    fileKey,
                    accessUrl,
                    r2Service.signSinglePut(fileKey, mimeType).toString(),
                    null,
                    null,
                    null,
                    r2Properties.getCacheControl(),
                    null,
                    null));
        }

        int partSizeBytes = (int) toBytes(r2Properties.getPartSizeMb());
        int partCount = (int) Math.ceil((double) req.getSizeBytes() / partSizeBytes);
        if (partCount > MAX_MULTIPART_PARTS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件过大，请提高 part-size-mb 后重试");
        }

        String uploadId = r2Service.createMultipart(fileKey, mimeType);
        List<R2PartUploadResp> parts = new ArrayList<>(partCount);
        List<java.net.URL> urls = r2Service.signParts(fileKey, uploadId, partCount);
        for (int i = 0; i < urls.size(); i++) {
            parts.add(new R2PartUploadResp(i + 1, urls.get(i).toString()));
        }
        return Result.success(new R2InitResp(
                MODE_MULTIPART,
                fileKey,
                accessUrl,
                null,
                uploadId,
                partCount,
                partSizeBytes,
                r2Properties.getCacheControl(),
                parts,
                null));
    }

    @PostMapping("/complete")
    public Result<R2CompleteResp> complete(@Valid @RequestBody R2CompleteReq req) {
        String uploaderId = requireCurrentUserId();
        requireR2Enabled();

        MediaFile existing = mediaFileService.findActiveByFileKey(req.getFileKey());
        if (existing != null) {
            return Result.success(new R2CompleteResp(existing.getId(), existing.getAccessUrl()));
        }

        String mode = normalizeMode(req.getMode());
        String mediaType = normalizeMediaType(req.getMediaType());
        String mimeType = normalizeMimeType(req.getMimeType(), mediaType);
        String ext = resolveExtension(req.getOriginalName(), mimeType);
        validateFile(req.getSizeBytes(), mediaType, ext);

        if (MODE_MULTIPART.equals(mode)) {
            if (!StringUtils.hasText(req.getUploadId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "multipart 上传必须携带 uploadId");
            }
            Map<Integer, String> etags = toEtagMap(req.getEtags());
            validateMultipartEtags(req.getSizeBytes(), etags);
            r2Service.complete(req.getFileKey(), req.getUploadId(), etags);
        }

        verifyUploadedObject(req.getFileKey(), req.getSizeBytes(), mode);

        String accessUrl = r2Service.buildAccessUrl(req.getFileKey());
        MediaFile saved = mediaFileService.saveCompletedFile(new MediaFile()
                .setId(IdUtils.genId("MEDIA"))
                .setFileKey(req.getFileKey())
                .setOriginalName(req.getOriginalName().trim())
                .setMimeType(mimeType)
                .setMediaType(mediaType)
                .setSizeBytes(req.getSizeBytes())
                .setSha256(normalizeOptional(req.getSha256()))
                .setAccessUrl(accessUrl)
                .setUploaderId(uploaderId)
                .setBizType(normalizeOptional(req.getBizType()))
                .setBizId(normalizeOptional(req.getBizId()))
                .setStatus(1));
        return Result.success(new R2CompleteResp(saved.getId(), accessUrl));
    }

    @PostMapping("/abort")
    public Result<Void> abort(@Valid @RequestBody R2AbortReq req) {
        requireCurrentUserId();
        requireR2Enabled();
        r2Service.abort(req.getFileKey().trim(), req.getUploadId().trim());
        return Result.success();
    }

    private Map<Integer, String> toEtagMap(List<R2PartEtagReq> etags) {
        Map<Integer, String> map = new LinkedHashMap<>();
        if (etags == null) {
            return map;
        }
        for (R2PartEtagReq item : etags) {
            if (item == null || item.getPartNumber() == null || !StringUtils.hasText(item.getEtag())) {
                continue;
            }
            map.put(item.getPartNumber(), item.getEtag().trim());
        }
        return map;
    }

    private void validateMultipartEtags(long sizeBytes, Map<Integer, String> etags) {
        if (etags.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "multipart 上传必须携带 ETag 列表");
        }
        int expectedPartCount = calculatePartCount(sizeBytes);
        if (etags.size() != expectedPartCount) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "multipart 分片信息不完整，期望 " + expectedPartCount + " 片，实际收到 " + etags.size() + " 片");
        }
        for (int partNumber = 1; partNumber <= expectedPartCount; partNumber++) {
            if (!etags.containsKey(partNumber)) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "multipart 分片信息缺失，缺少第 " + partNumber + " 片，请重新上传");
            }
        }
    }

    private int calculatePartCount(long sizeBytes) {
        int partSizeBytes = (int) toBytes(r2Properties.getPartSizeMb());
        return (int) Math.ceil((double) sizeBytes / partSizeBytes);
    }

    private void verifyUploadedObject(String fileKey, long expectedSize, String mode) {
        HeadObjectResponse lastHead = null;
        for (int attempt = 1; attempt <= HEAD_VERIFY_MAX_ATTEMPTS; attempt++) {
            lastHead = r2Service.head(fileKey);
            if (lastHead != null && lastHead.contentLength() == expectedSize) {
                return;
            }
            if (attempt < HEAD_VERIFY_MAX_ATTEMPTS) {
                waitBeforeRetry();
            }
        }

        if (lastHead == null) {
            log.warn("R2 对象校验失败: mode={}, fileKey={}, expectedSize={}, actualSize=<missing>",
                    mode, fileKey, expectedSize);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "R2 对象不存在，上传尚未完成");
        }

        log.warn("R2 对象大小校验失败: mode={}, fileKey={}, expectedSize={}, actualSize={}, attempts={}",
                mode, fileKey, expectedSize, lastHead.contentLength(), HEAD_VERIFY_MAX_ATTEMPTS);
        throw new BusinessException(ResultCode.SYSTEM_ERROR,
                "R2 对象大小校验失败，请重试上传（期望 " + expectedSize + " 字节，实际 " + lastHead.contentLength() + " 字节）");
    }

    private void waitBeforeRetry() {
        try {
            TimeUnit.MILLISECONDS.sleep(HEAD_VERIFY_RETRY_INTERVAL_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "上传校验被中断，请重试");
        }
    }

    private void validateFile(long sizeBytes, String mediaType, String ext) {
        if (sizeBytes <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件大小必须大于 0");
        }
        long maxSize = "image".equals(mediaType)
                ? toBytes(r2Properties.getMaxImageSizeMb())
                : toBytes(r2Properties.getMaxVideoSizeMb());
        if (sizeBytes > maxSize) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件大小超过限制");
        }
        if (!isAllowedExtension(mediaType, ext)) {
            throw new BusinessException(ResultCode.FILE_FORMAT_ERROR, "文件扩展名不受支持");
        }
    }

    private boolean isAllowedExtension(String mediaType, String ext) {
        String allowed = "image".equals(mediaType)
                ? r2Properties.getAllowedImageExtensions()
                : r2Properties.getAllowedVideoExtensions();
        if (!StringUtils.hasText(ext) || !StringUtils.hasText(allowed)) {
            return false;
        }
        String normalizedExt = ext.startsWith(".") ? ext.toLowerCase(Locale.ROOT) : "." + ext.toLowerCase(Locale.ROOT);
        for (String item : allowed.split(",")) {
            String value = item == null ? "" : item.trim().toLowerCase(Locale.ROOT);
            if (normalizedExt.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private String resolveExtension(String originalName, String mimeType) {
        if (StringUtils.hasText(originalName)) {
            String name = originalName.trim();
            int index = name.lastIndexOf('.');
            if (index >= 0 && index < name.length() - 1) {
                return name.substring(index).toLowerCase(Locale.ROOT);
            }
        }
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case MediaType.IMAGE_JPEG_VALUE -> ".jpg";
            case MediaType.IMAGE_PNG_VALUE -> ".png";
            case MediaType.IMAGE_GIF_VALUE -> ".gif";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/ogg" -> ".ogg";
            case "video/quicktime" -> ".mov";
            default -> throw new BusinessException(ResultCode.FILE_FORMAT_ERROR, "无法识别文件扩展名");
        };
    }

    private String normalizeMimeType(String mimeType, String mediaType) {
        if (!StringUtils.hasText(mimeType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "mimeType 不能为空");
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        if ("image".equals(mediaType) && !normalized.startsWith("image/")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "图片 mimeType 非法");
        }
        if ("video".equals(mediaType) && !normalized.startsWith("video/")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "视频 mimeType 非法");
        }
        return normalized;
    }

    private String normalizeMediaType(String mediaType) {
        if (!StringUtils.hasText(mediaType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "mediaType 不能为空");
        }
        String normalized = mediaType.trim().toLowerCase(Locale.ROOT);
        if (!"image".equals(normalized) && !"video".equals(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "mediaType 仅支持 image/video");
        }
        return normalized;
    }

    private String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "mode 不能为空");
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (!MODE_SINGLE.equals(normalized) && !MODE_MULTIPART.equals(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "mode 仅支持 single/multipart");
        }
        return normalized;
    }

    private long toBytes(int sizeMb) {
        return (long) sizeMb * 1024 * 1024;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String requireCurrentUserId() {
        String userId = SecurityUtils.getCurrentUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.TOKEN_MISSING, "请先登录后再上传文件");
        }
        return userId;
    }

    private void requireR2Enabled() {
        if (!r2Properties.isEnabled()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "R2 上传未启用");
        }
    }
}
