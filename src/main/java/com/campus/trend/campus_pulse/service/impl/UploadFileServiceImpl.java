package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.exception.custom.FileEmptyException;
import com.campus.trend.campus_pulse.exception.custom.FileFormatException;
import com.campus.trend.campus_pulse.exception.custom.InvalidFileNameException;
import com.campus.trend.campus_pulse.service.UploadFileService;
import com.campus.trend.campus_pulse.service.VideoTranscodeService;
import com.campus.trend.campus_pulse.utils.FileUtils;
import com.campus.trend.campus_pulse.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class UploadFileServiceImpl implements UploadFileService {

    private final VideoTranscodeService videoTranscodeService;

    @Value("${campus.upload.url-prefix:/uploads/}")
    private String urlPrefix = "/uploads/";

    @Value("${campus.upload.avatar-module:avatar}")
    private String avatarModule = "avatar";

    @Value("${campus.upload.max-image-size-mb:30}")
    private long maxImageSizeMb = 30L;

    @Value("${campus.upload.allowed-image-extensions:.jpg,.jpeg,.png,.gif,.webp}")
    private String allowedImageExtensionsRaw = ".jpg,.jpeg,.png,.gif,.webp";

    @Value("${campus.upload.max-image-width:1920}")
    private int maxImageWidth = 1920;

    @Value("${campus.upload.max-image-height:1080}")
    private int maxImageHeight = 1080;

    @Value("${campus.upload.jpeg-quality:0.85}")
    private float imageJpegQuality = 0.85f;

    @Value("${campus.upload.max-video-size-mb:512}")
    private long maxVideoSizeMb = 512L;

    @Value("${campus.upload.allowed-video-extensions:.mp4,.webm,.ogg,.mov}")
    private String allowedVideoExtensionsRaw = ".mp4,.webm,.ogg,.mov";

    @Value("${campus.upload.enable-malware-placeholder-scan:true}")
    private boolean enableMalwarePlaceholderScan = true;

    @Value("${campus.upload.imagebed-url:}")
    private String imagebedUrl;

    @Value("${campus.upload.imagebed-api-key:}")
    private String imagebedApiKey;

    @Value("${campus.upload.imagebed-threshold-mb:3}")
    private long imagebedThresholdMb;

    private static final Pattern SAFE_MODULE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,32}$");
    private static final Set<String> ALLOWED_IMAGE_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Set<String> ALLOWED_VIDEO_MIME_TYPES = Set.of(
            "video/mp4", "video/webm", "video/ogg", "video/quicktime");
    private static final String EICAR_SIGNATURE = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @Override
    public String uploadImage(MultipartFile file, String module) {
        if (imagebedUrl != null && !imagebedUrl.isBlank()
                && imagebedApiKey != null && !imagebedApiKey.isBlank()
                && file.getSize() > imagebedThresholdMb * 1024 * 1024) {
            return uploadToImagebed(file, module);
        }
        return uploadFile(file, module, "image/", maxImageSizeMb, allowedImageExtensionsRaw, "图片", "IMG");
    }

    private String uploadToImagebed(MultipartFile file, String folder) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + imagebedApiKey);

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);
            body.add("folder", folder);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(imagebedUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String url = (String) response.getBody().get("url");
                log.info("图片上传至图床成功: folder={}, url={}", folder, url);
                return url;
            }
            throw new BusinessException(ResultCode.FAILED, "图床上传失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("图床上传异常，降级到本地存储: {}", e.getMessage());
            return uploadFile(file, folder, "image/", maxImageSizeMb, allowedImageExtensionsRaw, "图片", "IMG");
        }
    }

    @Override
    public String uploadVideo(MultipartFile file, String module) {
        return uploadFile(file, module, "video/", maxVideoSizeMb, allowedVideoExtensionsRaw, "视频", "VID");
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        return uploadFile(file, avatarModule, "image/", maxImageSizeMb, allowedImageExtensionsRaw, "头像", "AVT");
    }

    private String uploadFile(MultipartFile file,
                              String module,
                              String contentTypePrefix,
                              long maxSizeMb,
                              String allowedExtensionsRaw,
                              String fileLabel,
                              String idPrefix) {
        validateNotEmpty(file);
        validateFileSize(file, maxSizeMb, fileLabel);

        String safeModule = normalizeModule(module);
        if (safeModule == null) {
            throw new FileFormatException("非法模块名");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null || !contentType.startsWith(contentTypePrefix)) {
            throw new FileFormatException("只能上传" + fileLabel + "文件");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (!allowedExtensions(allowedExtensionsRaw).contains(extension)) {
            throw new FileFormatException("不支持的" + fileLabel + "格式");
        }
        if (!isAllowedMimeType(contentTypePrefix, contentType)) {
            throw new FileFormatException(fileLabel + "MIME类型不在白名单内");
        }
        if (!isMimeCompatibleWithExtension(extension, contentType)) {
            throw new FileFormatException(fileLabel + "扩展名与MIME类型不匹配");
        }

        LocalDate today = LocalDate.now();
        String year = String.format(Locale.ROOT, "%04d", today.getYear());
        String month = String.format(Locale.ROOT, "%02d", today.getMonthValue());
        String day = String.format(Locale.ROOT, "%02d", today.getDayOfMonth());
        String datePath = String.join("/", year, month, day);
        Path targetPath = Paths.get(FileUtils.getProjectRootPath(), "data", "uploads", safeModule, year, month, day);
        String newFilename = IdUtils.genId(idPrefix) + extension;
        Path filePath = targetPath.resolve(newFilename);

        log.info("{}上传开始: module={}, origin={}, size={}B", fileLabel, safeModule, originalFilename, file.getSize());

        try {
            Files.createDirectories(targetPath);
            runMalwarePlaceholderScan(file, originalFilename);

            if ("image/".equals(contentTypePrefix)) {
                saveImageOptimized(file, filePath, extension);
            } else {
                file.transferTo(filePath.toFile());
                videoTranscodeService.submitTranscodeIfNeeded(filePath, extension);
            }

            String url = normalizeUrlPrefix() + safeModule + "/" + datePath + "/" + newFilename;
            log.info("{}上传成功: module={}, url={}", fileLabel, safeModule, url);
            return url;
        } catch (IllegalArgumentException e) {
            log.warn("{}上传校验失败: module={}, file={}, err={}", fileLabel, safeModule, originalFilename, e.getMessage());
            throw new FileFormatException(e.getMessage());
        } catch (IOException e) {
            log.error("{}上传失败: module={}, file={}", fileLabel, safeModule, originalFilename, e);
            throw new BusinessException(ResultCode.FAILED, fileLabel + "上传失败: " + e.getMessage());
        }
    }

    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileEmptyException("文件不能为空");
        }
    }

    private void validateFileSize(MultipartFile file, long maxSizeMb, String fileLabel) {
        if (file.getSize() > maxSizeMb * 1024 * 1024) {
            throw new FileFormatException(fileLabel + "大小不能超过 " + maxSizeMb + "MB");
        }
    }

    private String normalizeModule(String module) {
        if (module == null) {
            return null;
        }
        String value = module.trim();
        if (!SAFE_MODULE_PATTERN.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidFileNameException("文件名无效");
        }
        int dotIndex = originalFilename.lastIndexOf(".");
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            throw new InvalidFileNameException("文件扩展名非法");
        }
        return originalFilename.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private Set<String> allowedExtensions(String allowedExtensionsRaw) {
        return Arrays.stream(allowedExtensionsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private String normalizeUrlPrefix() {
        String prefix = urlPrefix == null ? "/uploads/" : urlPrefix.trim();
        if (prefix.isEmpty()) {
            prefix = "/uploads/";
        }
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        return contentType.toLowerCase(Locale.ROOT).trim();
    }

    private void saveImageOptimized(MultipartFile file, Path filePath, String extension) throws IOException {
        if (".gif".equals(extension) || ".webp".equals(extension)) {
            file.transferTo(filePath.toFile());
            return;
        }

        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage original = ImageIO.read(inputStream);
            if (original == null) {
                throw new IllegalArgumentException("图片内容解析失败，疑似伪造文件");
            }

            BufferedImage normalized = resizeIfNeeded(original);
            if (".jpg".equals(extension) || ".jpeg".equals(extension)) {
                writeJpeg(normalized, filePath);
            } else {
                ImageIO.write(normalized, "png", filePath.toFile());
            }
        }
    }

    private BufferedImage resizeIfNeeded(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) {
            return source;
        }

        double widthScale = maxImageWidth > 0 ? (double) maxImageWidth / width : 1.0;
        double heightScale = maxImageHeight > 0 ? (double) maxImageHeight / height : 1.0;
        double scale = Math.min(1.0, Math.min(widthScale, heightScale));
        if (scale >= 1.0) {
            return source;
        }

        int targetW = Math.max(1, (int) Math.round(width * scale));
        int targetH = Math.max(1, (int) Math.round(height * scale));
        int imageType = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(targetW, targetH, imageType);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, targetW, targetH, null);
        g.dispose();
        return resized;
    }

    private void writeJpeg(BufferedImage image, Path filePath) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpg", filePath.toFile());
            return;
        }
        ImageWriter writer = writers.next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        if (writeParam.canWriteCompressed()) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(Math.max(0.3f, Math.min(1.0f, imageJpegQuality)));
        }

        try (FileImageOutputStream output = new FileImageOutputStream(filePath.toFile())) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            writer.dispose();
        }
    }

    private boolean isAllowedMimeType(String contentTypePrefix, String contentType) {
        Set<String> allowedMimeTypes = "image/".equals(contentTypePrefix)
                ? ALLOWED_IMAGE_MIME_TYPES
                : ALLOWED_VIDEO_MIME_TYPES;
        return allowedMimeTypes.contains(contentType);
    }

    private boolean isMimeCompatibleWithExtension(String extension, String contentType) {
        return switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg".equals(contentType);
            case ".png" -> "image/png".equals(contentType);
            case ".gif" -> "image/gif".equals(contentType);
            case ".webp" -> "image/webp".equals(contentType);
            case ".mp4" -> "video/mp4".equals(contentType);
            case ".webm" -> "video/webm".equals(contentType);
            case ".ogg" -> "video/ogg".equals(contentType);
            case ".mov" -> "video/quicktime".equals(contentType);
            default -> false;
        };
    }

    private void runMalwarePlaceholderScan(MultipartFile file, String originalFilename) throws IOException {
        if (!enableMalwarePlaceholderScan) {
            return;
        }
        try (InputStream inputStream = file.getInputStream()) {
            byte[] sample = inputStream.readNBytes(8192);
            String sampleText = new String(sample, StandardCharsets.US_ASCII);
            if (sampleText.contains(EICAR_SIGNATURE)) {
                throw new IllegalArgumentException("文件安全扫描未通过，请更换文件后重试");
            }
        }
        log.debug("文件安全扫描占位通过: {}", originalFilename);
    }
}
