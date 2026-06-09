package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户徽章表
 */
@Data
@Accessors(chain = true)
@TableName("user_badges")
public class UserBadge implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 徽章类型: expert/contributor/moderator/early_bird/quality_answer
     */
    private String badgeType;

    /**
     * 徽章关联分类（如板块ID或标签名）
     */
    private String badgeCategory;

    /**
     * 徽章显示名称
     */
    private String badgeName;

    /**
     * 徽章描述
     */
    private String badgeDesc;

    /**
     * 徽章图标
     */
    private String badgeIcon;

    /**
     * 徽章颜色
     */
    private String badgeColor;

    /**
     * 获得时间
     */
    private LocalDateTime earnedAt;

    /**
     * 过期时间（NULL表示永久）
     */
    private LocalDateTime expiryAt;

    /**
     * 状态 1=有效 0=已撤销
     */
    private Integer status;

    /**
     * 授予原因
     */
    private String grantReason;

    /**
     * 授予人（管理员/系统）
     */
    private String grantedBy;
}
