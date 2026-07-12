package com.campus.trend.campus_pulse.mapper;

import com.campus.trend.campus_pulse.dto.response.SupporterCreatorInsightDailyResp;
import com.campus.trend.campus_pulse.dto.response.SupporterCreatorInsightSummaryResp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupporterCreatorInsightsMapper {

    @Select("""
            SELECT
                COUNT(1) AS publishedPosts,
                COALESCE(SUM(GREATEST(COALESCE(p.view_count, 0), 0)), 0) AS totalViews,
                COALESCE(SUM(GREATEST(COALESCE(p.like_count, 0), 0)), 0) AS totalLikes,
                COALESCE(SUM(GREATEST(COALESCE(p.collect_count, 0), 0)), 0) AS totalCollects,
                COALESCE(SUM(GREATEST(COALESCE(p.comment_count, 0), 0)), 0) AS totalComments,
                FLOOR(COALESCE(
                    SUM(GREATEST(COALESCE(p.avg_dwell_sec, 0), 0) * GREATEST(COALESCE(p.view_count, 0), 0))
                    / NULLIF(SUM(GREATEST(COALESCE(p.view_count, 0), 0)), 0),
                    0
                )) AS avgDwellSec
            FROM sys_post p
            WHERE p.user_id = #{userId}
              AND p.status = 1
              AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED')
            """)
    SupporterCreatorInsightSummaryResp selectSummary(@Param("userId") String userId);

    @Select("""
            SELECT
                activity_date AS date,
                SUM(views) AS views,
                SUM(likes) AS likes,
                SUM(collects) AS collects,
                SUM(comments) AS comments,
                FLOOR(COALESCE(SUM(dwell_ms) / NULLIF(SUM(dwell_samples), 0), 0) / 1000) AS avgDwellSec
            FROM (
                SELECT DATE(v.create_time) AS activity_date,
                       COUNT(1) AS views, 0 AS likes, 0 AS collects, 0 AS comments,
                       SUM(GREATEST(COALESCE(v.duration_ms, 0), 0)) AS dwell_ms,
                       SUM(CASE WHEN COALESCE(v.duration_ms, 0) > 0 THEN 1 ELSE 0 END) AS dwell_samples
                  FROM sys_view_log v
                  JOIN sys_post p ON p.id = v.post_id
                 WHERE p.user_id = #{userId}
                   AND p.status = 1
                   AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED')
                   AND v.create_time >= #{fromInclusive}
                   AND v.create_time < #{toExclusive}
                 GROUP BY DATE(v.create_time)
                UNION ALL
                SELECT DATE(pl.create_time), 0, COUNT(1), 0, 0, 0, 0
                  FROM sys_post_like pl
                  JOIN sys_post p ON p.id = pl.post_id
                 WHERE p.user_id = #{userId}
                   AND p.status = 1
                   AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED')
                   AND pl.create_time >= #{fromInclusive}
                   AND pl.create_time < #{toExclusive}
                 GROUP BY DATE(pl.create_time)
                UNION ALL
                SELECT DATE(pc.create_time), 0, 0, COUNT(1), 0, 0, 0
                  FROM sys_post_collect pc
                  JOIN sys_post p ON p.id = pc.post_id
                 WHERE p.user_id = #{userId}
                   AND p.status = 1
                   AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED')
                   AND pc.create_time >= #{fromInclusive}
                   AND pc.create_time < #{toExclusive}
                 GROUP BY DATE(pc.create_time)
                UNION ALL
                SELECT DATE(c.create_time), 0, 0, 0, COUNT(1), 0, 0
                  FROM sys_comment c
                  JOIN sys_post p ON p.id = c.post_id
                 WHERE p.user_id = #{userId}
                   AND p.status = 1
                   AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED')
                   AND (c.audit_status IS NULL OR c.audit_status = '' OR c.audit_status = 'APPROVED')
                   AND c.create_time >= #{fromInclusive}
                   AND c.create_time < #{toExclusive}
                 GROUP BY DATE(c.create_time)
            ) daily_events
            GROUP BY activity_date
            ORDER BY activity_date
            """)
    List<SupporterCreatorInsightDailyResp> selectDailyTrend(
            @Param("userId") String userId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive);
}
