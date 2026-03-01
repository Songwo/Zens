package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.utils.FileUtils;
import com.campus.trend.campus_pulse.utils.IdUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Tag(name = "文件上传", description = "通用文件上传接口")
@RestController
@RequestMapping("/common")
@Slf4j
public class UploadController {

    @Value("${campus.upload.url-prefix:/uploads/}")
    private String urlPrefix;

    @Value("${campus.upload.max-image-size-mb:5}")
    private long maxImageSizeMb;

    @Value("${campus.upload.allowed-image-extensions:.jpg,.jpeg,.png,.gif,.webp}")
    private String allowedImageExtensionsRaw;

    private static final Pattern SAFE_MODULE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,32}$");

    @Operation(summary = "上传图片", description = "支持 jpg, png, jpeg, gif, webp")
    @PostMapping("/upload/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "module", defaultValue = "common") String module) {
        if (file.isEmpty()) {
            return Result.error(ResultCode.FILE_IS_NULL,"文件不能为空");
        }
        if (file.getSize() > maxImageSizeMb * 1024 * 1024) {
            return Result.error(ResultCode.FILE_FORMAT_ERROR, "图片大小不能超过 " + maxImageSizeMb + "MB");
        }

        String safeModule = normalizeModule(module);
        if (safeModule == null) {
            return Result.error(ResultCode.FILE_FORMAT_ERROR, "非法模块名");
        }

        // Song：1. 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error(ResultCode.FILE_FORMAT_ERROR,"只能上传图片文件");
        }

        // Song：2. 生成文件名
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return Result.error(ResultCode.FILE_FORMAT_ERROR, "文件扩展名非法");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        if (!allowedExtensions().contains(extension)) {
            return Result.error(ResultCode.FILE_FORMAT_ERROR, "不支持的图片格式");
        }

        String newFilename = IdUtils.genId("IMG") + extension;

        // Song：3. 生成日期目录
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        // Song：说明
        String projectRoot = FileUtils.getProjectRootPath();
        Path targetPath = Paths.get(projectRoot, "data", "uploads", safeModule, datePath);
        log.info("图片上传开始: module={}, origin={}, size={}B", safeModule, originalFilename, file.getSize());

        try {
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }

            // Song：4. 保存文件
            Path filePath = targetPath.resolve(newFilename);
            file.transferTo(filePath.toFile());

            // Song：说明
            String url = urlPrefix + safeModule + "/" + datePath + "/" + newFilename;
            log.info("图片上传成功: module={}, url={}", safeModule, url);
            return Result.success(url);

        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error(ResultCode.FAILED,"文件上传失败: " + e.getMessage());
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

    private Set<String> allowedExtensions() {
        return Arrays.stream(allowedImageExtensionsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
