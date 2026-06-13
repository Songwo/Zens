package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 签到状态 / 签到结果响应。
 * getStatus 时 reward* 为 0；checkIn 成功后 reward* 为本次所得，供前端弹奖励提示。
 */
@Data
public class CheckInStatusResp {

    /** 今日是否已签到 */
    private boolean checkedToday;

    /** 当前连续签到天数 */
    private int continuousDays;

    /** 累计签到天数 */
    private long totalDays;

    /** 本月已签到日期（ISO yyyy-MM-dd），用于日历点亮 */
    private List<String> monthDates;

    /** 本次签到获得积分（仅 checkIn 返回） */
    private int rewardPoints;

    /** 本次签到获得经验（仅 checkIn 返回） */
    private int rewardExp;

    /** 用户当前总积分 */
    private int totalPoints;
}
