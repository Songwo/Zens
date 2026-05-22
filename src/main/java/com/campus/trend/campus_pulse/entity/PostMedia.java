package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子媒体表。由 go-media-service 负责文件本体与元信息存储，
 * 该表只保存帖子 ↔ 媒体 的引用关系和展示所需的最小字段。
 */
@Data
@Accessors(chain = true)
@TableName("sys_post_media")
public class PostMedia implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属帖子 ID（sys_post.id） */
    private String postId;

    /** go-media-service 中的文件 ID（media_files.id） */
    private String fileId;

    /** 媒体类型：image / video */
    private String mediaType;

    /** 直接可访问的 URL（Go 端返回的 accessUrl） */
    private String accessUrl;

    /** 视频封面 URL；视频才有 */
    private String coverUrl;

    /** MIME */
    private String mimeType;

    /** 原始文件名，便于后台审核展示 */
    private String originalName;

    /** 字节大小 */
    private Long sizeBytes;

    /** 宽（像素） */
    private Integer width;

    /** 高（像素） */
    private Integer height;

    /** 视频时长（秒） */
    private Integer durationSeconds;

    /** 排序序号（0 开始） */
    private Integer sortOrder;

    /** 状态：1 正常 / 0 已隐藏/删除 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
