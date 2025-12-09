package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@TableName("sys_comment")
public class SysComment implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String postId;

    private String userId;

    private String content;

    /**
     * 父评论ID (0为根评论)
     */
    private String parentId;

    /**
     * 子评论列表 (非数据库字段)
     */
    @TableField(exist = false)
    private List<SysComment> children;

    /**
     * 被回复的人ID
     */
    private String replyUserId;

    /**
     * 是否匿名
     */
    private Integer isAnonymous;

    private Integer likeCount;

    private LocalDateTime createTime;
}
