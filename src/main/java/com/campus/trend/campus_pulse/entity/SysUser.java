package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户基础信息表
 */
@Data
@Accessors(chain = true)
@TableName("sys_user")
public class SysUser implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID) // 假设使用雪花算法生成ID
    private String id;

    private String username;

    private String password;

    private String nickname;

    private String avatar;

    /**
     * 性别 0:未知 1:男 2:女
     */
    private Integer gender;

    /**
     * 角色 0:管理员 1:学生 2:老师
     */
    private Integer role;

    private String school;

    private String major;

    private Integer grade;

    /**
     * 状态 1:正常 0:封禁
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime lastGradeUpgrade;

}