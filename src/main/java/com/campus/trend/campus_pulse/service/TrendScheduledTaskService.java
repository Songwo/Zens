package com.campus.trend.campus_pulse.service;

/**
 * Song：定时统计任务服务
 * Song：将调度触发与业务逻辑解耦，方便测试和复用。
 */
public interface TrendScheduledTaskService {

    /**
     * Song：执行每日等级升级任务
     */
    void runDailyLevelUpgrade();

    /**
     * Song：执行日志清理任务
     */
    void runViewLogCleanup();
}
