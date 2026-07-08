package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论点赞记录。持久化到 comment_likes 表（uk_comment_like_user_comment 唯一约束防重）。
 * 此前点赞状态只存 Redis（comment:like:* 永不过期键），Redis 数据丢失即状态漂移，现迁移到 DB。
 */
@Data
@Accessors(chain = true)
@TableName("comment_likes")
public class CommentLike implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String commentId;

    private String userId;

    private LocalDateTime createdAt;
}
