package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.service.VideoTranscodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Service
@Slf4j
public class VideoTranscodeServiceImpl implements VideoTranscodeService {

    @Value("${campus.upload.video-transcode-enabled:false}")
    private boolean videoTranscodeEnabled;

    @Value("${campus.upload.ffmpeg-bin:ffmpeg}")
    private String ffmpegBin;

    @Value("${campus.upload.video-transcode-crf:28}")
    private int transcodeCrf;

    @Override
    @Async("taskExecutor")
    public void submitTranscodeIfNeeded(Path filePath, String extension) {
        if (!videoTranscodeEnabled || filePath == null || extension == null) {
            return;
        }
        String ext = extension.toLowerCase(Locale.ROOT);
        // Song：仅对 mp4 做原位转码，其他格式不改 URL，避免资源地址不一致
        if (!".mp4".equals(ext)) {
            return;
        }

        Path tempOutput = filePath.resolveSibling(filePath.getFileName() + ".transcoded.mp4");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegBin,
                    "-y",
                    "-i", filePath.toString(),
                    "-vcodec", "libx264",
                    "-preset", "veryfast",
                    "-crf", String.valueOf(Math.max(18, Math.min(36, transcodeCrf))),
                    "-acodec", "aac",
                    "-movflags", "+faststart",
                    tempOutput.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0 || !Files.exists(tempOutput)) {
                Files.deleteIfExists(tempOutput);
                log.warn("视频转码失败: path={}, exitCode={}", filePath, exitCode);
                return;
            }

            long originSize = Files.size(filePath);
            long transcodedSize = Files.size(tempOutput);
            if (transcodedSize > 0 && transcodedSize < originSize) {
                Files.move(tempOutput, filePath, StandardCopyOption.REPLACE_EXISTING);
                log.info("视频转码完成并替换源文件: path={}, originSize={}, newSize={}",
                        filePath, originSize, transcodedSize);
            } else {
                Files.deleteIfExists(tempOutput);
                log.info("视频转码完成但体积未变小，保留原文件: path={}, originSize={}, newSize={}",
                        filePath, originSize, transcodedSize);
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tempOutput);
            } catch (Exception ignored) {
            }
            log.warn("异步视频转码异常: path={}, err={}", filePath, e.getMessage());
        }
    }
}

