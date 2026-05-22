package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.LevelExpRecordPageResp;
import com.campus.trend.campus_pulse.dto.response.LevelInfoResp;

public interface LevelService {

    void addExperience(String userId, int exp, String reason);

    LevelInfoResp getUserLevelInfo(String userId);

    /**
     * 查询用户经验变动记录（分页）。
     * @param userId   用户 ID
     * @param days     过滤最近多少天，null 表示不过滤
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     */
    LevelExpRecordPageResp getExperienceRecords(String userId, Integer days, int page, int pageSize);

    void processLevelUpgrade(String userId);

    void batchUpgradeAllUsers();
}
