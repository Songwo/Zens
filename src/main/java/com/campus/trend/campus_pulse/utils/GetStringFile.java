package com.campus.trend.campus_pulse.utils;

import com.campus.trend.campus_pulse.exception.definexception.FileGeShiException;
import com.campus.trend.campus_pulse.exception.definexception.FileNameFailException;
import com.campus.trend.campus_pulse.exception.definexception.FileIsNullException;
import org.springframework.web.multipart.MultipartFile;

public class GetStringFile {

    public static String getString(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileIsNullException("上传文件不能为空");
        }

        // 1.检查文件后缀，只允许 jpg/png/jpeg
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new FileNameFailException("文件名无效");
        }
        String suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        if (!suffix.matches("\\.(jpg|jpeg|png)$")) {
            throw new FileGeShiException("仅支持 jpg/jpeg/png 图片格式");
        }
        return suffix;
    }

}
