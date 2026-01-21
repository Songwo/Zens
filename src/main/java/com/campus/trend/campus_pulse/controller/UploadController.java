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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Tag(name = "文件上传", description = "通用文件上传接口")
@RestController
@RequestMapping("/common")
@Slf4j
public class UploadController {

    @Value("${campus.upload.url-prefix:/uploads/}")
    private String urlPrefix;

    @Operation(summary = "上传图片", description = "支持 jpg, png, jpeg, gif, webp")
    @PostMapping("/upload/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error(ResultCode.FILE_IS_NULL,"文件不能为空");
        }

        // 1. 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error(ResultCode.FILE_FORMAT_ERROR,"只能上传图片文件");
        }

        // 2. 生成文件名
        String originalFilename = file.getOriginalFilename();
        assert originalFilename != null;
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String newFilename = IdUtils.genId("IMG") + extension;

        // 3. 生成日期目录
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        // 使用项目根目录 + /data/uploads/ 保持与 WebMvcConfig 一致
        String projectRoot = FileUtils.getProjectRootPath();
        Path targetPath = Paths.get(projectRoot, "data", "uploads", datePath);

        try {
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }

            // 4. 保存文件
            Path filePath = targetPath.resolve(newFilename);
            file.transferTo(filePath.toFile());

            // 5. 返回URL
            String url = urlPrefix + datePath + "/" + newFilename;
            return Result.success(url);

        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error(ResultCode.FAILED,"文件上传失败: " + e.getMessage());
        }
    }
}
