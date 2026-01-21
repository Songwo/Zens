package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 趋势统计缓存表 - 存储定时任务计算的统计结果
 */
@TableName("sys_trend_stat")
@Accessors(chain = true)
@Data
public class TrendStat implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private Date statDate;

    private String type;

    private String dataJson;

    private LocalDateTime createTime;

}
