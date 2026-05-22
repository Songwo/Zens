package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("sys_media_file")
public class MediaFile implements Serializable {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String fileKey;

    private String originalName;

    private String mimeType;

    private String mediaType;

    private Long sizeBytes;

    private String sha256;

    private Integer width;

    private Integer height;

    private Integer durationSeconds;

    private String accessUrl;

    private String coverUrl;

    private String uploaderId;

    private String bizType;

    private String bizId;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
