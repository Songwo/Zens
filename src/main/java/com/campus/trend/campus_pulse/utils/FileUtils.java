package com.campus.trend.campus_pulse.utils;

import com.campus.trend.campus_pulse.exception.custom.FileEmptyException;
import com.campus.trend.campus_pulse.exception.custom.FileFormatException;
import com.campus.trend.campus_pulse.exception.custom.InvalidFileNameException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件工具类
 */
public class FileUtils {

    /**
     * 获取项目根目录
     */
    public static String getProjectRootPath() {
        return System.getProperty("user.dir");
    }

    /**
     * 校验图片文件并获取后缀
     * @param file 文件
     * @return 文件后缀 (e.g. ".jpg")
     */
    public static String validateImageAndGetSuffix(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileEmptyException("上传文件不能为空");
        }

        // 1.检查文件后缀，只允许 jpg/png/jpeg
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new InvalidFileNameException("文件名无效");
        }
        String suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        if (!suffix.matches("\\.(jpg|jpeg|png)$")) {
            throw new FileFormatException("仅支持 jpg/jpeg/png 图片格式");
        }
        return suffix;
    }
}
