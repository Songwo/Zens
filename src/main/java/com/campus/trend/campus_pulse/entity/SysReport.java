package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Song：举报信息表
 */
@Data
@Accessors(chain = true)
@TableName("sys_report")
public class SysReport implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String targetType;

    private String targetId;

    /**
     * Song：举报原因/类型 (例如: 违规违法, 色情低俗, 广告营销 等)
     */
    private String reason;

    /**
     * Song：举报的具体说明/详情
     */
    private String details;

    private String reporterId;

    /**
     * Song：举报人当时的信任等级，用于加权
     */
    private Integer reporterTrustLevel;

    /**
     * Song：flag 权重 = 举报人 TL 映射值（TL3→5, TL2→3, 其它→1）
     */
    private Integer flagWeight;

    /**
     * Song：状态: 0=待处理, 1=已处理, 2=已忽略
     */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
