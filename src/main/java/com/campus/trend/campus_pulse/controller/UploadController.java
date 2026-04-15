package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.service.UploadFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "文件上传", description = "通用文件上传接口")
@RestController
@RequestMapping("/common")
@RequiredArgsConstructor
public class UploadController {

    private final UploadFileService uploadFileService;

    @Operation(summary = "上传图片", description = "支持 jpg, png, jpeg, gif, webp")
    @PostMapping("/upload/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "module", defaultValue = "common") String module) {
        return Result.success(uploadFileService.uploadImage(file, module));
    }

    @Operation(summary = "上传视频", description = "支持 mp4, webm, ogg, mov")
    @PostMapping("/upload/video")
    public Result<String> uploadVideo(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "module", defaultValue = "common") String module) {
        return Result.success(uploadFileService.uploadVideo(file, module));
    }
}
