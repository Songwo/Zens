package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.entity.SysTrendStat;
import com.campus.trend.campus_pulse.service.TrendStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 趋势统计控制器 - 用于数据看板和趋势展示
 */
@Slf4j
@RestController
@RequestMapping("/sys-trend-stat")
@RequiredArgsConstructor
public class TrendStatController {

    private final TrendStatService trendStatService;

    /**
     * 获取关键词词云数据
     */
    @GetMapping("/keyword-cloud")
    public Result<?> getKeywordCloud() {
        Map<String, Object> data = trendStatService.getKeywordCloud();
        return Result.success(data);
    }

    /**
     * 获取分类饼图数据
     */
    @GetMapping("/category-pie")
    public Result<?> getCategoryPie() {
        Map<String, Object> data = trendStatService.getCategoryPie();
        return Result.success(data);
    }

    /**
     * 获取热度排行数据
     */
    @GetMapping("/heat-rank")
    public Result<?> getHeatRank() {
        List<Map<String, Object>> data = trendStatService.getHeatRank();

        // 如果缓存数据为空，实时从数据库生成
        if (data == null || data.isEmpty()) {
            log.info("缓存的热度排行数据为空，实时生成");
            data = generateRealtimeHeatRank();
        }

        return Result.success(data);
    }

    /**
     * 实时生成热度排行（当缓存数据不存在时）
     */
    private List<Map<String, Object>> generateRealtimeHeatRank() {
        // 注入PostService来获取热门帖子
        // 这里简化处理，直接返回空列表
        // 实际应该查询 sys_post 表按 heat_score 排序
        return new ArrayList<>();
    }

    /**
     * 获取指定类型的最新统计数据
     */
    @GetMapping("/latest/{type}")
    public Result<?> getLatestByType(@PathVariable String type) {
        SysTrendStat stat = trendStatService.getLatestStatByType(type);
        return Result.success(stat);
    }

    /**
     * 获取指定日期和类型的统计数据
     */
    @GetMapping("/by-date")
    public Result<?> getByDateAndType(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date statDate,
            @RequestParam String type) {
        SysTrendStat stat = trendStatService.getStatByDateAndType(statDate, type);
        return Result.success(stat);
    }

    /**
     * 获取时间范围内的统计数据
     */
    @GetMapping("/range")
    public Result<?> getStatsByRange(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
            @RequestParam(required = false) String type) {
        List<SysTrendStat> stats = trendStatService.getStatsByDateRange(startDate, endDate, type);
        return Result.success(stats);
    }

    /**
     * 保存或更新统计数据（管理员/定时任务）
     */
    @PostMapping("/save")
    public Result<?> saveOrUpdate(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date statDate,
            @RequestParam String type,
            @RequestBody String dataJson) {
        trendStatService.saveOrUpdateStat(statDate, type, dataJson);
        return Result.success();
    }

    /**
     * 触发生成当日统计（管理员/定时任务）
     */
    @PostMapping("/generate-daily")
    public Result<?> generateDailyStats() {
        trendStatService.generateDailyStats();
        return Result.success();
    }

    /**
     * 删除指定日期之前的统计数据（管理员）
     */
    @DeleteMapping("/clean")
    public Result<?> cleanOldStats(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date beforeDate) {
        long deletedCount = trendStatService.deleteStatsBefore(beforeDate);
        return Result.success(deletedCount);
    }

}
