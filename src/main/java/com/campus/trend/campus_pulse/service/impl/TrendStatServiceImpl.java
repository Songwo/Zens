package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.TrendStatService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrendStatServiceImpl implements TrendStatService {

    private final PostMapper postMapper;
    private final SectionMapper sectionMapper;
    private final UserMapper userMapper;

    @Override
    public Map<String, Object> getKeywordCloud() {
        List<Post> posts = postMapper.selectList(
                new QueryWrapper<Post>().select("tags").isNotNull("tags").last("LIMIT 100"));

        Map<String, Integer> tagFreq = new HashMap<>();
        for (Post post : posts) {
            if (post.getTags() != null && !post.getTags().isEmpty()) {
                for (String tag : post.getTags().split(" ")) {
                    tag = tag.replace("#", "").trim();
                    if (!tag.isEmpty()) {
                        tagFreq.merge(tag, 1, Integer::sum);
                    }
                }
            }
        }

        List<Map<String, Object>> keywords = new ArrayList<>();
        tagFreq.forEach((k, v) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("keyword", k);
            item.put("count", v);
            keywords.add(item);
        });
        keywords.sort((a, b) -> ((Integer) b.get("count")).compareTo((Integer) a.get("count")));

        Map<String, Object> result = new HashMap<>();
        result.put("keywords", keywords.size() > 30 ? keywords.subList(0, 30) : keywords);
        return result;
    }

    @Override
    public List<Map<String, Object>> getPostTrend() {
        return postMapper.selectMaps(
                new QueryWrapper<Post>()
                        .select("DATE_FORMAT(create_time, '%Y-%m-%d') as date", "count(*) as count")
                        .groupBy("DATE_FORMAT(create_time, '%Y-%m-%d')")
                        .orderByAsc("date")
                        .last("LIMIT 7"));
    }

    @Override
    public List<Map<String, Object>> getUserTrend() {
        return userMapper.selectMaps(
                new QueryWrapper<User>()
                        .select("DATE_FORMAT(create_time, '%Y-%m-%d') as date", "count(*) as count")
                        .groupBy("DATE_FORMAT(create_time, '%Y-%m-%d')")
                        .orderByAsc("date")
                        .last("LIMIT 7"));
    }

    @Override
    public List<Map<String, Object>> getTrendPrediction() {
        Map<String, Object> cloud = getKeywordCloud();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keywords = (List<Map<String, Object>>) cloud.getOrDefault("keywords",
                new ArrayList<>());

        List<Map<String, Object>> prediction = new ArrayList<>();
        int limit = Math.min(keywords.size(), 5);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> kw = keywords.get(i);
            String name = (String) kw.get("keyword");

            Map<String, Object> item = new HashMap<>();
            item.put("topic", name);
            double growth = 15.0 + (new Random().nextDouble() * 135.0);
            item.put("growthRate", Math.round(growth * 10) / 10.0);
            String status = growth > 80 ? "rising" : (growth > 30 ? "stable" : "falling");
            item.put("status", status);
            item.put("insight", generateInsight(name, status));
            prediction.add(item);
        }
        return prediction;
    }

    @Override
    public Map<String, Object> getSectionPie() {
        List<Map<String, Object>> stats = postMapper.selectMaps(
                new QueryWrapper<Post>().select("section_id", "count(*) as count").groupBy("section_id"));

        Map<String, Object> result = new HashMap<>();
        for (Map<String, Object> item : stats) {
            Object sectionIdObj = item.get("section_id");
            String sectionName = "未分类";
            if (sectionIdObj != null) {
                var section = sectionMapper.selectById(Long.valueOf(sectionIdObj.toString()));
                if (section != null)
                    sectionName = section.getName();
            }
            result.put(sectionName, item.get("count"));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getHeatRank() {
        return getHeatRank(1, 10);
    }

    @Override
    public List<Map<String, Object>> getHeatRank(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safePageSize;

        List<Post> posts = postMapper.selectList(
                new QueryWrapper<Post>().orderByDesc("heat_score").last("LIMIT " + offset + ", " + safePageSize));

        List<Map<String, Object>> rank = new ArrayList<>();
        for (Post post : posts) {
            Map<String, Object> item = new HashMap<>();
            item.put("postId", post.getId());
            item.put("title", post.getTitle());
            item.put("heatScore", post.getHeatScore() != null ? post.getHeatScore() : 0.0);
            item.put("viewCount", post.getViewCount() != null ? post.getViewCount() : 0);
            rank.add(item);
        }
        return rank;
    }

    private String generateInsight(String topic, String status) {
        return switch (status) {
            case "rising" -> "话题「" + topic + "」讨论热度正在急剧上升，建议重点关注。";
            case "stable" -> "「" + topic + "」保持稳定讨论热度，内容趋于高质量深度交流。";
            default -> "「" + topic + "」热度有所回落，讨论焦点可能发生转移。";
        };
    }
}
