package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.SysTrendStat;
import com.campus.trend.campus_pulse.mapper.SysTrendStatMapper;
import com.campus.trend.campus_pulse.service.TrendStatService;
import com.campus.trend.campus_pulse.utils.GenerateIDUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 趋势统计服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TrendStatServiceImpl extends ServiceImpl<SysTrendStatMapper, SysTrendStat>
        implements TrendStatService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateStat(Date statDate, String type, String dataJson) {
        SysTrendStat existing = getStatByDateAndType(statDate, type);

        if (existing != null) {
            existing.setDataJson(dataJson);
            existing.setCreateTime(LocalDateTime.now());
            updateById(existing);
            log.info("更新统计数据: {} - {}", type, statDate);
        } else {
            SysTrendStat stat = new SysTrendStat()
                    .setId(GenerateIDUtil.genId("STAT"))
                    .setStatDate(statDate)
                    .setType(type)
                    .setDataJson(dataJson)
                    .setCreateTime(LocalDateTime.now());
            save(stat);
            log.info("创建统计数据: {} - {}", type, statDate);
        }
    }

    @Override
    public SysTrendStat getStatByDateAndType(Date statDate, String type) {
        return lambdaQuery()
                .eq(SysTrendStat::getStatDate, statDate)
                .eq(SysTrendStat::getType, type)
                .one();
    }

    @Override
    public SysTrendStat getLatestStatByType(String type) {
        return lambdaQuery()
                .eq(SysTrendStat::getType, type)
                .orderByDesc(SysTrendStat::getStatDate)
                .orderByDesc(SysTrendStat::getCreateTime)
                .last("LIMIT 1")
                .one();
    }

    @Override
    public Map<String, Object> getKeywordCloud() {
        SysTrendStat stat = getLatestStatByType(TYPE_KEYWORD_CLOUD);
        return parseJsonToMap(stat);
    }

    @Override
    public Map<String, Object> getCategoryPie() {
        SysTrendStat stat = getLatestStatByType(TYPE_CATEGORY_PIE);
        return parseJsonToMap(stat);
    }

    @Override
    public List<Map<String, Object>> getHeatRank() {
        SysTrendStat stat = getLatestStatByType(TYPE_HEAT_RANK);
        if (stat == null || stat.getDataJson() == null) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(stat.getDataJson(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });
        } catch (Exception e) {
            log.error("解析热度排行数据失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<SysTrendStat> getStatsByDateRange(Date startDate, Date endDate, String type) {
        return lambdaQuery()
                .eq(type != null, SysTrendStat::getType, type)
                .ge(startDate != null, SysTrendStat::getStatDate, startDate)
                .le(endDate != null, SysTrendStat::getStatDate, endDate)
                .orderByDesc(SysTrendStat::getStatDate)
                .list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long deleteStatsBefore(Date beforeDate) {
        long count = lambdaQuery()
                .lt(SysTrendStat::getStatDate, beforeDate)
                .count();

        if (count > 0) {
            remove(lambdaQuery()
                    .lt(SysTrendStat::getStatDate, beforeDate)
                    .getWrapper());
            log.info("删除了 {} 条旧统计数据", count);
        }

        return count;
    }

    @Override
    public void generateDailyStats() {
        // 这个方法通常由定时任务调用
        // 实际统计逻辑需要根据业务需求实现
        log.info("开始生成每日统计数据...");

        Date today = new Date();

        // 示例：保存一个简单的统计数据
        Map<String, Object> statsData = new HashMap<>();
        statsData.put("generated_at", LocalDateTime.now().toString());
        statsData.put("type", "daily_summary");

        try {
            String json = objectMapper.writeValueAsString(statsData);
            saveOrUpdateStat(today, "daily_summary", json);
        } catch (Exception e) {
            log.error("生成每日统计数据失败", e);
        }
    }

    /**
     * 解析JSON字符串为Map
     */
    private Map<String, Object> parseJsonToMap(SysTrendStat stat) {
        if (stat == null || stat.getDataJson() == null) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(stat.getDataJson(),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            log.error("解析统计数据失败: {}", stat.getType(), e);
            return Collections.emptyMap();
        }
    }

}
