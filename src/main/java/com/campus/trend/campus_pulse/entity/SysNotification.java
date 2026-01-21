package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_notification")
public class SysNotification {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String userId; // Receiver
    private String senderId; // Triggered by whom (optional)
    private String senderName;
    private String senderAvatar;

    private String title;
    private String content;
    private Integer type; // 1: System, 2: Reply, 3: Like, 4: Collect
    private String relatedId; // Post ID or Comment ID

    private Integer status; // 0: Unread, 1: Read
    private LocalDateTime createTime;
}
