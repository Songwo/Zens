package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子订阅表（主题追踪）。
 * 一人对一帖最多一条记录（唯一约束 user_id+post_id）。
 */
@Data
@Accessors(chain = true)
@TableName("post_subscription")
public class PostSubscription implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订阅用户ID
     */
    private String userId;

    /**
     * 被订阅帖子ID
     */
    private String postId;

    /**
     * 订阅来源: auto(评论/发帖自动) / manual(手动追踪)
     */
    private String source;

    private LocalDateTime createdAt;
}
