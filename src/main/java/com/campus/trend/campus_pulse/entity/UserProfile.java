package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户动态画像表 - 用于推荐系统
 */
@Data
@Accessors(chain = true)
@TableName(value = "sys_user_profile", autoResultMap = true) // 开启自动映射以支持JSON
public class UserProfile implements Serializable {

    @TableId(value = "user_id") // 这里ID不是自增，而是关联sys_user的ID
    private String userId;

    /**
     * 信誉积分
     */
    private Integer reputation;

    /**
     * 社区贡献值
     */
    private Integer contributionVal;

    /**
     * 常活跃地点
     */
    private String activeRegion;

    /**
     * 偏好分类权重 JSON
     * 在数据库存的是 {"news":0.8, "life":0.2}
     * 在 Java 中直接映射为 Map
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Double> preferredCateJson;

    private Integer totalPosts;

    private Integer totalLikesReceived;

    private LocalDateTime lastActiveTime;
}