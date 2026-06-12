package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子投票表（一帖 0..1，发帖时创建）
 */
@Data
@Accessors(chain = true)
@TableName("poll")
public class Poll implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联帖子ID（1:1）
     */
    private String postId;

    /**
     * 投票问题，空则前端用帖子标题兜底
     */
    private String title;

    /**
     * 0单选 1多选
     */
    private Integer multiChoice;

    /**
     * 多选时最多可选项数；单选恒为1；0=不限
     */
    private Integer maxChoices;

    /**
     * 截止时间，空=不限期
     */
    private LocalDateTime deadline;

    /**
     * 1进行中 0已关闭
     */
    private Integer status;

    /**
     * 参与投票的去重人数（冗余缓存）
     */
    private Integer voterCount;

    /**
     * 创建者（帖子作者）
     */
    private String createdBy;

    private LocalDateTime createdAt;
}
