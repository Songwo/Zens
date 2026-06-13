package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("short_links")
public class ShortLink implements Serializable {

    @TableId(value = "code", type = IdType.INPUT)
    private String code;

    private String targetType;

    private String postId;

    private String commentId;

    private String creatorId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
