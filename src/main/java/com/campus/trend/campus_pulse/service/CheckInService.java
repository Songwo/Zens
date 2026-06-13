package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.CheckInStatusResp;

/**
 * 每日签到服务
 */
public interface CheckInService {

    /** 查询签到状态（是否已签、连续天数、本月签到日期等） */
    CheckInStatusResp getStatus(String userId);

    /** 执行签到：写记录、发积分与经验，返回本次奖励与最新状态 */
    CheckInStatusResp checkIn(String userId);
}
