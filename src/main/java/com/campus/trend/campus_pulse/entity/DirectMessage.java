package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 私信消息
 */
@Data
@Accessors(chain = true)
@TableName("direct_messages")
public class DirectMessage implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String conversationId;
    private String senderId;
    private String receiverId;
    private String content;
    private Integer isRead;
    private LocalDateTime createdAt;
}
