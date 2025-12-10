package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 浏览日志表 - 用于数据分析和热度统计
 */
@TableName("sys_view_log")
@Accessors(chain = true)
@Data
public class SysViewLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String postId;
    private String userId;
    /**
     * 访问Ip
     */
    private String ip;
    /**
     * 设备类型
     */
    private String device;
    private LocalDateTime createTime;

}
