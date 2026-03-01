package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.LevelInfoResp;
import com.campus.trend.campus_pulse.dto.response.LevelExpRecordResp;

import java.util.Map;

public interface LevelService {

    void addExperience(String userId, int exp, String reason);

    LevelInfoResp getUserLevelInfo(String userId);

    /**
     * Song：查询经验记录
     *
     * Song：说明
     * Song：说明
     * Song：说明
     * Song：说明
     */
    Map<String, Object> getExperienceRecords(String userId, Integer days, int page, int pageSize);

    void processLevelUpgrade(String userId);

    void batchUpgradeAllUsers();
}
