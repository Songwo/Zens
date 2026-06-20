package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SSO 应用注册实体
 */
@Data
@Accessors(chain = true)
@TableName("sys_sso_client")
public class SsoClient implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 应用标识（唯一） */
    private String clientId;

    /** 应用名称 */
    private String clientName;

    /** 应用密钥 */
    private String clientSecret;

    /** 回调地址 */
    private String redirectUri;

    /** 应用描述 */
    private String description;

    /** 应用 Logo URL */
    private String logoUrl;

    /** 是否启用 1:是 0:否 */
    private Integer enabled;

    /** 第一方可信 1:自动授权跳过同意页 0:需手动同意 */
    private Integer trusted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
