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
public class Comment implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String postId;

    private String userId;

    private String content;

    /**
     * Song：说明
     */
    private String parentId;

    /**
     * Song：子评论列表 (非数据库字段)
     */
    @TableField(exist = false)
    private List<Comment> children;

    /**
     * Song：说明
     */
    private String replyToUserId;

    /**
     * Song：说明
     */
    private String replyUserId;

    /**
     * Song：是否匿名
     */
    private Integer isAnonymous;

    private Integer likeCount;

    private LocalDateTime createTime;
}
