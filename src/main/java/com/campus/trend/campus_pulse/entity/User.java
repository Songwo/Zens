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

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 用户角色列表（非数据库字段，用于返回给前端）
     */
    @TableField(exist = false)
    private List<String> roles;

}
