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
@TableName("comment_collects")
public class CommentCollect implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String commentId;

    private String userId;

    private LocalDateTime createdAt;
}
