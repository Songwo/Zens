package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SysTrendStat;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 趋势统计服务接口 - 用于缓存和查询统计数据
 */
public interface TrendStatService extends IService<SysTrendStat> {

    // ========== 统计类型常量 ==========
    String TYPE_KEYWORD_CLOUD = "keyword_cloud"; // 关键词词云
    String TYPE_CATEGORY_PIE = "category_pie"; // 分类饼图
    String TYPE_HEAT_RANK = "heat_rank"; // 热度排行
    String TYPE_DAILY_POST = "daily_post"; // 每日发帖量
    String TYPE_DAILY_VIEW = "daily_view"; // 每日浏览量
    String TYPE_USER_ACTIVE = "user_active"; // 活跃用户统计

    /**
     * 保存或更新统计数据
     * 
     * @param statDate 统计日期
     * @param type     统计类型
     * @param dataJson 统计数据JSON
     */
    void saveOrUpdateStat(Date statDate, String type, String dataJson);

    /**
     * 获取指定日期和类型的统计数据
     * 
     * @param statDate 统计日期
     * @param type     统计类型
     * @return 统计实体
     */
    SysTrendStat getStatByDateAndType(Date statDate, String type);

    /**
     * 获取最新的统计数据（按类型）
     * 
     * @param type 统计类型
     * @return 最新的统计实体
     */
    SysTrendStat getLatestStatByType(String type);

    /**
     * 获取关键词词云数据
     * 
     * @return 词云数据Map
     */
    /**
     * 获取最近7天的帖子发布趋势
     */
    List<Map<String, Object>> getPostTrend();

    Map<String, Object> getKeywordCloud();

    /**
     * 获取分类饼图数据
     * 
     * @return 分类统计数据
     */
    Map<String, Object> getCategoryPie();

    /**
     * 获取热度排行数据
     * 
     * @return 热度排行列表
     */
    List<Map<String, Object>> getHeatRank();

    /**
     * 获取指定时间范围内的所有统计数据
     * 
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param type      统计类型
     * @return 统计列表
     */
    List<SysTrendStat> getStatsByDateRange(Date startDate, Date endDate, String type);

    /**
     * 删除指定日期之前的统计数据
     * 
     * @param beforeDate 截止日期
     * @return 删除的记录数
     */
    long deleteStatsBefore(Date beforeDate);

    /**
     * 触发生成当日统计数据（通常由定时任务调用）
     */
    void generateDailyStats();

}
