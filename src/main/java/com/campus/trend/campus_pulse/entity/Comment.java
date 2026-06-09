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

    private String parentId;

    /**
     * Song：子评论列表 (非数据库字段)
     */
    @TableField(exist = false)
    private List<Comment> children;

    private String replyToUserId;

    private String replyUserId;

    /**
     * Song：是否匿名
     */
    private Integer isAnonymous;

    private Integer likeCount;

    private Integer collectCount;

    /**
     * Song：是否被采纳为最佳答案
     */
    private Integer isAdopted;

    /**
     * 审核/删除状态: APPROVED | DELETED
     */
    private String auditStatus;

    private LocalDateTime createTime;

    /**
     * 最后更新时间(软删 3 天倒计时基准)
     */
    private LocalDateTime updateTime;

    /**
     * 最后编辑时间(由作者/版主/管理员修改评论内容时写入；与软删倒计时无关)
     */
    private LocalDateTime editTime;
}
