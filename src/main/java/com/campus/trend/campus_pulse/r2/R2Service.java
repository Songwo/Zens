package com.campus.trend.campus_pulse.r2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * R2 Presign 服务：浏览器直传 R2，Java 只签 URL，不经手字节。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class R2Service {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final R2Properties props;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public String getBucket() {
        return props.getBucket();
    }

    public String getPublicBaseUrl() {
        return props.getPublicBaseUrl();
    }

    /** 生成与 Go 老服务相同结构的 file key：images/yyyy/MM/dd/<uuid><ext> 或 videos/... */
    public String genFileKey(String mediaType, String ext) {
        String prefix = "image".equalsIgnoreCase(mediaType) ? "images" : "videos";
        String dateDir = LocalDate.now().format(DATE_DIR);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String safeExt = (ext == null || ext.isBlank()) ? "" : (ext.startsWith(".") ? ext : "." + ext);
        return prefix + "/" + dateDir + "/" + uuid + safeExt.toLowerCase(Locale.ROOT);
    }

    /** 公开访问 URL（R2 自定义域 + 同 key） */
    public String buildAccessUrl(String fileKey) {
        String base = props.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("campus.r2.public-base-url 未配置");
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + fileKey;
    }

    /** 单 PUT 直传：≤ singlePutThresholdMb 的小文件用这个 */
    public URL signSinglePut(String fileKey, String contentType) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(fileKey)
                .contentType(contentType);
        if (StringUtils.hasText(props.getCacheControl())) {
            builder.cacheControl(props.getCacheControl().trim());
        }
        PutObjectRequest objectRequest = builder.build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(b -> b
                .signatureDuration(Duration.ofSeconds(props.getPresignTtlSeconds()))
                .putObjectRequest(objectRequest));
        return presigned.url();
    }

    /** 创建分片上传，返回 uploadId */
    public String createMultipart(String fileKey, String contentType) {
        CreateMultipartUploadRequest.Builder builder = CreateMultipartUploadRequest.builder()
                .bucket(props.getBucket())
                .key(fileKey)
                .contentType(contentType);
        if (StringUtils.hasText(props.getCacheControl())) {
            builder.cacheControl(props.getCacheControl().trim());
        }
        CreateMultipartUploadRequest req = builder.build();
        CreateMultipartUploadResponse resp = s3Client.createMultipartUpload(req);
        return resp.uploadId();
    }

    /** 为每个分片签发 URL（partNumber 从 1 起） */
    public List<URL> signParts(String fileKey, String uploadId, int partCount) {
        List<URL> urls = new ArrayList<>(partCount);
        Duration ttl = Duration.ofSeconds(props.getPresignTtlSeconds());
        for (int i = 1; i <= partCount; i++) {
            int partNumber = i;
            UploadPartRequest req = UploadPartRequest.builder()
                    .bucket(props.getBucket())
                    .key(fileKey)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();
            PresignedUploadPartRequest p = presigner.presignUploadPart(b -> b
                    .signatureDuration(ttl)
                    .uploadPartRequest(req));
            urls.add(p.url());
        }
        return urls;
    }

    /** 合并分片：etagsByPart 为 partNumber→ETag */
    public void complete(String fileKey, String uploadId, Map<Integer, String> etagsByPart) {
        List<CompletedPart> parts = new ArrayList<>(etagsByPart.size());
        etagsByPart.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> parts.add(CompletedPart.builder()
                        .partNumber(e.getKey())
                        .eTag(e.getValue())
                        .build()));
        CompletedMultipartUpload completed = CompletedMultipartUpload.builder()
                .parts(parts)
                .build();
        CompleteMultipartUploadRequest req = CompleteMultipartUploadRequest.builder()
                .bucket(props.getBucket())
                .key(fileKey)
                .uploadId(uploadId)
                .multipartUpload(completed)
                .build();
        s3Client.completeMultipartUpload(req);
    }

    /** 终止分片上传（清理 R2 上的临时数据） */
    public void abort(String fileKey, String uploadId) {
        try {
            AbortMultipartUploadRequest req = AbortMultipartUploadRequest.builder()
                    .bucket(props.getBucket())
                    .key(fileKey)
                    .uploadId(uploadId)
                    .build();
            s3Client.abortMultipartUpload(req);
        } catch (Exception ex) {
            log.warn("R2 abortMultipartUpload 失败 key={}, uploadId={}, err={}", fileKey, uploadId, ex.getMessage());
        }
    }

    /** 删除对象（管理面板 / 软删后清理 R2 实物） */
    public void delete(String fileKey) {
        try {
            DeleteObjectRequest req = DeleteObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(fileKey)
                    .build();
            s3Client.deleteObject(req);
        } catch (Exception ex) {
            log.warn("R2 deleteObject 失败 key={}, err={}", fileKey, ex.getMessage());
        }
    }

    /** HEAD 检查文件是否真的传上去了 */
    public HeadObjectResponse head(String fileKey) {
        try {
            HeadObjectRequest req = HeadObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(fileKey)
                    .build();
            return s3Client.headObject(req);
        } catch (NoSuchKeyException e) {
            return null;
        }
    }
}
