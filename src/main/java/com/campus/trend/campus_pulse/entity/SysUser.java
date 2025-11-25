package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户实体类
 * 对应数据库表：sys_user
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true) // 开启链式操作
@TableName("sys_user") // 映射数据库表名
public class SysUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID (主键)
     */
    @TableId(value = "id")
    private String id;

    /**
     * 用户名（登录账号 / 学号）
     */
    private String username;

    /**
     * 密码（存储加密后的哈希值）
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 角色类型：0-管理员, 1-学生
     */
    private Integer role;

    /**
     * 专业
     */
    private String major;

    /**
     * 年级
     */
    private Integer grade;

    /**
     * 兴趣标签JSON
     */
    private String interestTags;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}