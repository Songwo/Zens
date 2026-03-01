package com.campus.trend.campus_pulse.utils;

import com.campus.trend.campus_pulse.exception.custom.FileEmptyException;
import com.campus.trend.campus_pulse.exception.custom.FileFormatException;
import com.campus.trend.campus_pulse.exception.custom.InvalidFileNameException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Song：文件工具类
 */
public class FileUtils {

    /**
     * Song：获取项目根目录
     */
    public static String getProjectRootPath() {
        return System.getProperty("user.dir");
    }

    /**
     * Song：校验图片文件并获取后缀
     * Song：说明
     * Song：说明
     */
    public static String validateImageAndGetSuffix(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileEmptyException("上传文件不能为空");
        }

        // Song：说明
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
