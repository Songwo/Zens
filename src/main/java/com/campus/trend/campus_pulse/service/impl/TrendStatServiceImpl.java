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
