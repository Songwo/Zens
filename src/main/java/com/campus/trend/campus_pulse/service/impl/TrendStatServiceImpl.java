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
    private final com.campus.trend.campus_pulse.mapper.SysPostMapper sysPostMapper;
    private final com.campus.trend.campus_pulse.mapper.SysCategoryMapper sysCategoryMapper;

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
    public List<Map<String, Object>> getPostTrend() {
        // 查询最近7天的数据
        // 注意：数据库函数 DATE(create_time) 依赖于 MySQL
        List<Map<String, Object>> list = sysPostMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.campus.trend.campus_pulse.entity.SysPost>()
                        .select("DATE_FORMAT(create_time, '%Y-%m-%d') as date", "count(*) as count")
                        .groupBy("DATE_FORMAT(create_time, '%Y-%m-%d')")
                        .orderByAsc("date")
                        .last("LIMIT 7"));
        return list;
    }

    @Override
    public Map<String, Object> getKeywordCloud() {
        SysTrendStat stat = getLatestStatByType(TYPE_KEYWORD_CLOUD);
        Map<String, Object> result = parseJsonToMap(stat);

        // 如果没有预生成数据，实时从帖子标签生成
        if (result.isEmpty()) {
            result = new HashMap<>(); // Fix: Ensure map is mutable
            List<com.campus.trend.campus_pulse.entity.SysPost> posts = sysPostMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.campus.trend.campus_pulse.entity.SysPost>()
                            .select("tags")
                            .isNotNull("tags")
                            .last("LIMIT 100") // 取最近100条
            );

            // 简单统计标签频率
            Map<String, Integer> tagFreq = new HashMap<>();
            for (com.campus.trend.campus_pulse.entity.SysPost post : posts) {
                if (post.getTags() != null && !post.getTags().isEmpty()) {
                    String[] tags = post.getTags().split(" ");
                    for (String tag : tags) {
                        tag = tag.replace("#", "").trim();
                        if (!tag.isEmpty()) {
                            tagFreq.put(tag, tagFreq.getOrDefault(tag, 0) + 1);
                        }
                    }
                }
            }

            // 转换为前端需要的格式 List<{name, value}>
            List<Map<String, Object>> keywords = new ArrayList<>();
            tagFreq.forEach((k, v) -> {
                Map<String, Object> item = new HashMap<>();
                item.put("keyword", k); // Frontend expects "keyword" not "name"
                item.put("count", v); // Frontend expects "count" not "value"
                keywords.add(item);
            });

            // 按频率排序并取Top 30
            keywords.sort((a, b) -> ((Integer) b.get("count")).compareTo((Integer) a.get("count")));
            result.put("keywords", keywords.size() > 30 ? keywords.subList(0, 30) : keywords);
        }

        return result;
    }

    @Override
    public Map<String, Object> getCategoryPie() {
        SysTrendStat stat = getLatestStatByType(TYPE_CATEGORY_PIE);
        Map<String, Object> result = parseJsonToMap(stat);

        // 实时生成
        if (result.isEmpty()) {
            result = new HashMap<>(); // Fix: Ensure map is mutable
            // 统计分类帖子数量
            List<Map<String, Object>> categoryStats = sysPostMapper.selectMaps(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.campus.trend.campus_pulse.entity.SysPost>()
                            .select("category_id", "count(*) as count")
                            .groupBy("category_id"));

            List<Map<String, Object>> pieData = new ArrayList<>();
            for (Map<String, Object> item : categoryStats) {
                Map<String, Object> pieItem = new HashMap<>();
                String catId = (String) item.get("category_id");
                String catName = "未分类";
                if (catId != null) {
                    com.campus.trend.campus_pulse.entity.SysCategory category = sysCategoryMapper.selectById(catId);
                    if (category != null) {
                        catName = category.getName();
                    }
                }

                pieItem.put("name", catName);
                pieItem.put("count", item.get("count")); // Frontend expects "count"
                pieData.add(pieItem);
            }
            result.put("categories", pieData); // Frontend expects "categories"
            // Calculate total
            long total = pieData.stream().mapToLong(m -> ((Number) m.get("count")).longValue()).sum();
            result.put("total", total > 0 ? total : 1);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getHeatRank() {
        // 先尝试从缓存获取
        SysTrendStat stat = getLatestStatByType(TYPE_HEAT_RANK);
        if (stat != null && stat.getDataJson() != null) {
            try {
                return objectMapper.readValue(stat.getDataJson(),
                        new TypeReference<List<Map<String, Object>>>() {
                        });
            } catch (Exception e) {
                log.warn("解析热度排行数据失败，将实时生成", e);
            }
        }

        // 如果没有预生成的数据，实时从数据库查询生成
        log.info("实时生成热度排行数据");
        return generateRealtimeHeatRank();
    }

    /**
     * 实时生成热度排行
     */
    private List<Map<String, Object>> generateRealtimeHeatRank() {
        try {
            // 从sys_post表查询热度最高的10条记录
            List<com.campus.trend.campus_pulse.entity.SysPost> posts = sysPostMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.campus.trend.campus_pulse.entity.SysPost>()
                            .orderByDesc("heat_score")
                            .last("LIMIT 10"));

            List<Map<String, Object>> heatRank = new ArrayList<>();
            for (com.campus.trend.campus_pulse.entity.SysPost post : posts) {
                Map<String, Object> item = new HashMap<>();
                item.put("postId", post.getId());
                item.put("title", post.getTitle());
                // heatScore可能为null，给予默认值
                item.put("heatScore", post.getHeatScore() != null ? post.getHeatScore() : 0.0);
                item.put("viewCount", post.getViewCount() != null ? post.getViewCount() : 0);
                heatRank.add(item);
            }
            return heatRank;
        } catch (Exception e) {
            log.error("生成实时热度排行失败", e);
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

    @Override
    public List<Map<String, Object>> getTrendPrediction() {
        // 1. 获取当前热门标签和频次
        Map<String, Object> currentCloud = getKeywordCloud();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keywords = (List<Map<String, Object>>) currentCloud.getOrDefault("keywords",
                new ArrayList<>());

        List<Map<String, Object>> prediction = new ArrayList<>();

        // 2. 只预测前5个最热门话题
        int limit = Math.min(keywords.size(), 5);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> kw = keywords.get(i);
            String name = (String) kw.get("keyword");
            int count = (Integer) kw.get("count");
            log.debug("Processing keyword: {}, count: {}", name, count);

            Map<String, Object> item = new HashMap<>();
            item.put("topic", name);

            // 3. 模拟增长率和趋势 (实际可根据历史同比/环比计算)
            // 这里我们模拟一个 15% - 150% 的增长率
            double growth = 15.0 + (new Random().nextDouble() * 135.0);
            item.put("growth", Math.round(growth * 10) / 10.0);

            // 状态判断
            String status = growth > 80 ? "rising" : (growth > 30 ? "stable" : "falling");
            item.put("status", status);

            // 4. 生成模拟AI洞察建议
            String insight = generateInsight(name, status);
            item.put("insight", insight);

            prediction.add(item);
        }

        return prediction;
    }

    private String generateInsight(String topic, String status) {
        if ("rising".equals(status)) {
            return "话题「" + topic + "」讨论热度正在急剧上升，建议重点关注。相关讨论主要集中在近期突发事件。";
        } else if ("stable".equals(status)) {
            return "「" + topic + "」保持稳定讨论热度。用户群体相对固定，内容趋于高质量深度交流。";
        } else {
            return "「" + topic + "」热度有所回落。可能由于周期性话题结束，或讨论焦点发生转移。";
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
