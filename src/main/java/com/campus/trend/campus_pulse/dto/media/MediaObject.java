package com.campus.trend.campus_pulse.dto.media;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 帖子与上传流程共用的媒体对象。
 * 字段保持向后兼容，便于前端透传与 sys_post_media 映射。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaObject {

    /** 任务 ID（分片/异步场景） */
    private String taskId;

    /** 文件 ID；Java 端作为 post_media.file_id 存储 */
    private String fileId;

    /** image / video */
    private String mediaType;

    /** 完整访问 URL */
    private String accessUrl;

    /** 视频封面 URL（视频才有） */
    private String coverUrl;

    /** 最终对象名 */
    private String fileName;

    /** 原始文件名 */
    private String originalName;

    /** MIME 类型 */
    private String mimeType;

    /** 字节大小 */
    private Long sizeBytes;

    /** SHA256 内容哈希 */
    private String sha256;

    /** 图片宽（视频若做过探测也会回填） */
    private Integer width;

    private Integer height;

    /** 视频时长（秒） */
    private Integer durationSeconds;

    /** 是否命中秒传 */
    private Boolean instantUpload;

    /** 记录创建时间 */
    private Instant createdAt;
}
