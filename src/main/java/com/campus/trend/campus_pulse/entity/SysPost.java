package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@TableName(value = "sys_post", autoResultMap = true)
public class SysPost implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String userId;

    private String categoryId;

    private String title;

    private String content;

    /**
     * 图片列表
     * 数据库存 ["url1", "url2"]
     * Java 直接映射为 List<String>
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /**
     * 冗余存储的标签字符串，用于简单展示 (如 "#考研 #Java")
     * 复杂的标签分析走 sys_tag 关联逻辑
     */
    private String tags;

    /**
     * 是否匿名 1:是 0:否
     */
    private Integer isAnonymous;

    private String locationName;

    /**
     * 情感分数
     */
    private BigDecimal sentimentScore;

    /**
     * 状态 1:正常 0:删除
     */
    private Integer status;

    /**
     * 审核状态: PENDING/APPROVED/REJECTED
     */
    private String auditStatus;

    // 统计数据
    private Integer viewCount;
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;
    private Double heatScore;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}