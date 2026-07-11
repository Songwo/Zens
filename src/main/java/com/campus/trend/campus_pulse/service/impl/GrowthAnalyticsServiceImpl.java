package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.dto.request.GrowthEventReq;
import com.campus.trend.campus_pulse.service.GrowthAnalyticsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GrowthAnalyticsServiceImpl implements GrowthAnalyticsService {
    static final Set<String> ALLOWED_EVENTS = Set.of(
            "page_view", "post_read", "signup_completed", "topics_selected",
            "post_collected", "user_followed", "comment_created", "post_created", "subscription_created");
    private static final Set<String> ALLOWED_PROPERTIES = Set.of("postId", "sectionId", "topicCount", "readSeconds");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(GrowthEventReq request, String userId) {
        String event = request.getEventName() == null ? "" : request.getEventName().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EVENTS.contains(event)) throw new IllegalArgumentException("不支持的统计事件");
        Map<String, Object> safe = new LinkedHashMap<>();
        if (request.getProperties() != null) {
            request.getProperties().forEach((key, value) -> {
                if (ALLOWED_PROPERTIES.contains(key) && (value instanceof Number || value instanceof Boolean || value instanceof String)) {
                    safe.put(key, String.valueOf(value).substring(0, Math.min(String.valueOf(value).length(), 100)));
                }
            });
        }
        try {
            int updated = jdbc.update("INSERT INTO growth_event(event_name,user_id,anonymous_id,session_id,route,source,properties_json) VALUES (?,?,?,?,?,?,CAST(? AS JSON))",
                    event, userId, token(request.getAnonymousId()), token(request.getSessionId()), safeRoute(request.getRoute()),
                    safeSource(request.getSource()), objectMapper.writeValueAsString(safe));
            if (updated != 1) throw new IllegalStateException("增长事件写入未生效");
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("统计属性格式无效", e);
        }
    }

    @Override
    public Map<String, Object> dashboard(int requestedDays) {
        int days = Math.min(Math.max(requestedDays, 7), 90);
        String actor = "COALESCE(user_id, CONCAT('a:', anonymous_id))";
        Map<String, Object> overview = jdbc.queryForMap("""
                SELECT COUNT(DISTINCT CASE WHEN event_name='page_view' THEN %s END) visitors,
                       COUNT(DISTINCT CASE WHEN event_name='signup_completed' THEN %s END) registrations,
                       COUNT(DISTINCT CASE WHEN event_name IN ('topics_selected','post_collected','user_followed','comment_created','post_created') THEN %s END) activated,
                       COUNT(DISTINCT CASE WHEN event_name IN ('comment_created','post_created') THEN %s END) contributors,
                       COUNT(DISTINCT CASE WHEN event_name='subscription_created' THEN %s END) supporters
                FROM growth_event WHERE create_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                """.formatted(actor, actor, actor, actor, actor), days);

        List<Map<String, Object>> daily = jdbc.queryForList("""
                SELECT DATE_FORMAT(create_time, '%Y-%m-%d') date,
                       COUNT(DISTINCT %s) activeUsers,
                       SUM(event_name='page_view') pageViews,
                       COUNT(DISTINCT CASE WHEN event_name IN ('comment_created','post_created','post_collected','user_followed') THEN %s END) engagedUsers
                FROM growth_event WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                GROUP BY DATE(create_time) ORDER BY DATE(create_time)
                """.formatted(actor, actor), days);

        List<Map<String, Object>> sources = jdbc.queryForList("""
                SELECT source, COUNT(DISTINCT %s) visitors
                FROM growth_event WHERE event_name='page_view' AND create_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                GROUP BY source ORDER BY visitors DESC LIMIT 10
                """.formatted(actor), days);

        List<Map<String, Object>> retention = jdbc.queryForList("""
                WITH first_seen AS (
                  SELECT %s actor, DATE(MIN(create_time)) cohort_date
                  FROM growth_event GROUP BY %s
                ), activity AS (
                  SELECT DISTINCT %s actor, DATE(create_time) activity_date FROM growth_event
                )
                SELECT DATE_FORMAT(f.cohort_date, '%%Y-%%m-%%d') cohortDate, COUNT(*) cohortSize,
                  ROUND(100 * SUM(EXISTS(SELECT 1 FROM activity a WHERE a.actor=f.actor AND DATEDIFF(a.activity_date,f.cohort_date)=1))/COUNT(*),1) d1,
                  ROUND(100 * SUM(EXISTS(SELECT 1 FROM activity a WHERE a.actor=f.actor AND DATEDIFF(a.activity_date,f.cohort_date) BETWEEN 6 AND 8))/COUNT(*),1) d7,
                  ROUND(100 * SUM(EXISTS(SELECT 1 FROM activity a WHERE a.actor=f.actor AND DATEDIFF(a.activity_date,f.cohort_date) BETWEEN 29 AND 31))/COUNT(*),1) d30
                FROM first_seen f WHERE f.cohort_date >= DATE_SUB(CURDATE(), INTERVAL 56 DAY)
                GROUP BY f.cohort_date ORDER BY f.cohort_date DESC LIMIT 12
                """.formatted(actor, actor, actor));

        return new LinkedHashMap<>(Map.of("days", days, "overview", overview, "daily", daily,
                "sources", sources, "retention", retention));
    }

    private String token(String value) {
        String normalized = value == null ? "" : value.replaceAll("[^a-zA-Z0-9_-]", "");
        if (normalized.length() < 8) throw new IllegalArgumentException("统计标识无效");
        return normalized.substring(0, Math.min(normalized.length(), 64));
    }

    private String safeRoute(String value) {
        if (value == null || value.isBlank()) return null;
        String route = value.split("[?#]", 2)[0];
        return route.startsWith("/") ? route.substring(0, Math.min(route.length(), 255)) : null;
    }

    private String safeSource(String value) {
        if (value == null || value.isBlank()) return "direct";
        String source = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        return source.isBlank() ? "direct" : source.substring(0, Math.min(source.length(), 40));
    }
}
