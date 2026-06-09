package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 打赏记录表
 */
@Data
@Accessors(chain = true)
@TableName("tip_records")
public class TipRecord implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 打赏者ID
     */
    private String tipperId;

    /**
     * 目标类型: post/comment
     */
    private String targetType;

    /**
     * 目标ID
     */
    private String targetId;

    /**
     * 被打赏者ID
     */
    private String targetAuthorId;

    /**
     * 打赏积分数
     */
    private Integer amount;

    /**
     * 打赏留言
     */
    private String message;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
