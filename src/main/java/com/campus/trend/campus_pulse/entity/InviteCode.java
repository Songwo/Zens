package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("invite_codes")
public class InviteCode {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 邀请码字符串（唯一） */
    private String code;

    /** 创建者用户ID（管理员或被授权用户） */
    private String creatorId;

    /** 被使用者用户ID */
    private String usedByUserId;

    /** 0=未使用 1=已使用 2=已禁用 */
    private Integer status;

    /** 最大使用次数（0=不限） */
    private Integer maxUses;

    /** 已使用次数 */
    private Integer usedCount;

    /** 过期时间（null=永不过期） */
    private LocalDateTime expireTime;

    /** 备注说明 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
