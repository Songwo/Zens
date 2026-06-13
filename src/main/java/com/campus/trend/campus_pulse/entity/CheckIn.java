package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户每日签到表
 */
@Data
@Accessors(chain = true)
@TableName("user_check_in")
public class CheckIn implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 签到日期
     */
    private LocalDate checkInDate;

    /**
     * 截至当日的连续签到天数
     */
    private Integer continuousDays;

    /**
     * 当日获得积分
     */
    private Integer rewardPoints;

    /**
     * 当日获得经验
     */
    private Integer rewardExp;

    private LocalDateTime createTime;
}
