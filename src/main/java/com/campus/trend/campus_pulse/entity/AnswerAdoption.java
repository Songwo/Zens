package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 答案采纳表
 */
@Data
@Accessors(chain = true)
@TableName("answer_adoptions")
public class AnswerAdoption implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 帖子ID
     */
    private String postId;

    /**
     * 被采纳的评论ID
     */
    private String commentId;

    /**
     * 采纳者（帖子作者）
     */
    private String adoptedBy;

    /**
     * 采纳时间
     */
    private LocalDateTime adoptedAt;

    /**
     * 授予的声望值
     */
    private Integer reputationGranted;

    /**
     * 授予的经验值
     */
    private Integer expGranted;
}
