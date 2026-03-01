package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Song：通知表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("notifications")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Song：说明
     */
    private String userId;

    /**
     * Song：说明
     */
    private String type;

    /**
     * Song：标题
     */
    private String title;

    /**
     * Song：内容
     */
    private String content;

    /**
     * Song：说明
     */
    private String relatedId;

    /**
     * Song：说明
     */
    private String relatedUserId;

    /**
     * Song：是否已读：0未读 1已读
     */
    private Integer isRead;

    /**
     * Song：创建时间
     */
    private LocalDateTime createdAt;
}
