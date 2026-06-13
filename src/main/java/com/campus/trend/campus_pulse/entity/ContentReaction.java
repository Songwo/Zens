package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 内容表情反应表（帖子/评论的 ❤️😆😮🎉，独立于点赞）
 */
@Data
@Accessors(chain = true)
@TableName("content_reaction")
public class ContentReaction implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 目标类型: post / comment
     */
    private String targetType;

    /**
     * 目标ID
     */
    private String targetId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 表情类型: love / haha / wow / celebrate
     */
    private String reactionType;

    private LocalDateTime createTime;
}
