package com.campus.trend.campus_pulse.mapper;

import com.campus.trend.campus_pulse.dto.response.TrustMetricsSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/** 当前用户行为画像的实时权威聚合。 */
@Mapper
public interface TrustMetricsMapper {

    @Select("""
            SELECT
                COUNT(DISTINCT DATE(v.create_time)) AS daysVisited,
                COUNT(DISTINCT CASE
                    WHEN v.create_time >= #{windowSince} THEN DATE(v.create_time)
                END) AS daysVisitedRecent,
                COUNT(DISTINCT CASE
                    WHEN v.create_time >= #{windowSince} AND COALESCE(v.duration_ms, 0) = 0
                    THEN v.post_id
                END) AS postsEnteredRecent,
                COALESCE(SUM(CASE
                    WHEN v.create_time >= #{windowSince} AND COALESCE(v.duration_ms, 0) = 0
                    THEN 1 ELSE 0
                END), 0) AS postsReadRecent,
                FLOOR(COALESCE(SUM(CASE
                    WHEN COALESCE(v.duration_ms, 0) > 0 THEN v.duration_ms ELSE 0
                END), 0) / 1000) AS readTimeSec,
                (
                    (SELECT COUNT(1) FROM sys_post_like pl WHERE pl.user_id = #{userId})
                    +
                    (SELECT COUNT(1) FROM comment_likes cl WHERE cl.user_id = #{userId})
                ) AS likesGiven,
                (
                    (SELECT COUNT(1)
                       FROM sys_post_like pl
                       JOIN sys_post p ON p.id = pl.post_id
                      WHERE p.user_id = #{userId}
                        AND p.status = 1
                        AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED'))
                    +
                    (SELECT COUNT(1)
                       FROM comment_likes cl
                       JOIN sys_comment c ON c.id = cl.comment_id
                       JOIN sys_post p ON p.id = c.post_id
                      WHERE c.user_id = #{userId}
                        AND p.status = 1
                        AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED')
                        AND (c.audit_status IS NULL OR c.audit_status = '' OR c.audit_status = 'APPROVED'))
                ) AS likesReceived,
                (SELECT COUNT(1)
                   FROM sys_post p
                  WHERE p.user_id = #{userId}
                    AND p.status = 1
                    AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED')) AS postsCreated
            FROM sys_view_log v
            WHERE v.user_id = #{userId}
            """)
    TrustMetricsSnapshot selectSnapshot(@Param("userId") String userId,
                                        @Param("windowSince") LocalDateTime windowSince);
}
