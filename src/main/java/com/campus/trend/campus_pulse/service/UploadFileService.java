package com.campus.trend.campus_pulse.service;

import org.springframework.web.multipart.MultipartFile;

public interface UploadFileService {

    String uploadImage(MultipartFile file, String module);

    String uploadVideo(MultipartFile file, String module);

    String uploadAvatar(MultipartFile file);
}
