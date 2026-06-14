package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户基础信息表
 */
@Data
@Accessors(chain = true)
@TableName(value = "sys_user", autoResultMap = true)
public class User implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID) // 使用雪花算法生成ID
    private String id;

    private String username;

    private String email;

    private String password;

    private String nickname;

    private String avatar;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 性别 0:未知 1:男 2:女
     */
    private Integer gender;

    private String school;

    private String major;

    private Integer enrollmentYear;

    /**
     * 用户等级 (1-10)
     */
    private Integer level;

    /**
     * 积分
     */
    private Integer points;

    /**
     * 经验值
     */
    private Integer experience;

    /**
     * 状态 1:正常 2:封禁
     */
    private Integer status;

    /**
     * 兴趣标签 (JSON字符串)
     */
    private String interestTags;

    /**
     * 角色: ROLE_USER/ROLE_ADMIN/ROLE_SUPER_ADMIN/ROLE_MODERATOR
     */
    private String role;

    // ===== profile 字段 (原 sys_user_profile) =====
    private Integer reputation;
    private Integer contributionVal;
    /**
     * 信任等级 0-4: TL0新人/TL1基础/TL2成员/TL3常客/TL4领袖（借鉴 Discourse 社区自治模型）
     */
    private Integer trustLevel;
    /**
     * 禁言截止时间，NULL=未禁言
     */
    private LocalDateTime silencedUntil;
    /**
     * 累计阅读时长（秒），用于 TL 计算
     */
    private Integer readTimeSec;
    /**
     * 累计访问天数（sys_view_log 去重日期）
     */
    private Integer daysVisited;
    /**
     * 发出点赞总数
     */
    private Integer likesGiven;
    private String activeRegion;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Double> preferredCateJson;

    private Integer totalPosts;
    private Integer totalLikesReceived;
    private LocalDateTime lastActiveTime;
    private String githubId;
    private String githubLogin;
    private Integer twoFactorEnabled;
    private String twoFactorSecret;
    private Integer emailNotifyEnabled;
    private String profileCardTheme;
    private String quickCardTheme;
    private String profileCardBgUrl;
    private String quickCardBgUrl;

    /**
     * 封面展示配置 (JSON: fit/x/y/height)
     */
    private String coverConfig;

    /**
     * 用户徽章文字（管理员授予的纯文字 flair，如「你可以访问L站」），空表示无徽章
     */
    private String badgeText;

    /**
     * 徽章颜色（纯色样式用，hex 如 #a855f7），空表示用默认色
     */
    private String badgeColor;

    /**
     * 徽章样式：solid=纯色 / rainbow=七彩跑马动效
     */
    private String badgeStyle;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 用户角色列表（非数据库字段，用于返回给前端）
     */
    @TableField(exist = false)
    private List<String> roles;

    /**
     * 已审核通过的板块版主范围（非数据库字段，用于返回给前端）
     */
    @TableField(exist = false)
    private List<Long> moderatedSectionIds;

}
