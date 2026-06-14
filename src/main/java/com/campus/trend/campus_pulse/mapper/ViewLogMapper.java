package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.dto.response.ViewHistoryDto;
import com.campus.trend.campus_pulse.entity.ViewLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ViewLogMapper extends BaseMapper<ViewLog> {

    @Select("""
            SELECT v.post_id as postId, 
                   MAX(p.title) as title, 
                   MAX(v.create_time) as viewTime
            FROM sys_view_log v
            LEFT JOIN sys_post p ON v.post_id = p.id
            WHERE v.user_id = #{userId}
            GROUP BY v.post_id
            ORDER BY viewTime DESC
            LIMIT #{limit}
            """)
    List<ViewHistoryDto> selectUserViewHistory(@Param("userId") String userId, @Param("limit") int limit);

    @Select("""
            SELECT v.post_id as postId,
                   MAX(p.title) as title,
                   MAX(v.create_time) as viewTime
            FROM sys_view_log v
            LEFT JOIN sys_post p ON v.post_id = p.id
            WHERE v.user_id = #{userId}
            GROUP BY v.post_id
            ORDER BY viewTime DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<ViewHistoryDto> selectUserViewHistoryPaged(@Param("userId") String userId,
                                                    @Param("offset") int offset,
                                                    @Param("pageSize") int pageSize);

    @Select("""
            SELECT COUNT(1) FROM (
                SELECT 1
                FROM sys_view_log
                WHERE user_id = #{userId}
                GROUP BY post_id
            ) t
            """)
    long countDistinctViewedPosts(@Param("userId") String userId);

    /**
     * Song：信任等级用 —— 最近 N 天内访问的不同日期数
     */
    @Select("""
            SELECT COUNT(DISTINCT DATE(create_time))
            FROM sys_view_log
            WHERE user_id = #{userId} AND create_time >= #{since}
            """)
    int countDistinctVisitedDays(@Param("userId") String userId, @Param("since") java.time.LocalDateTime since);

    /**
     * Song：信任等级用 —— 最近 N 天内进入的不同帖子数
     */
    @Select("""
            SELECT COUNT(DISTINCT post_id)
            FROM sys_view_log
            WHERE user_id = #{userId} AND create_time >= #{since}
            """)
    int countDistinctPostsEntered(@Param("userId") String userId, @Param("since") java.time.LocalDateTime since);

    /**
     * Song：信任等级用 —— 最近 N 天内阅读的帖子总数（含重复）
     */
    @Select("""
            SELECT COUNT(1)
            FROM sys_view_log
            WHERE user_id = #{userId} AND create_time >= #{since}
            """)
    int countPostViewsSince(@Param("userId") String userId, @Param("since") java.time.LocalDateTime since);

    /**
     * Song：信任等级用 —— 最近 N 天内累计阅读时长（秒）
     */
    @Select("""
            SELECT IFNULL(SUM(duration_ms), 0) / 1000
            FROM sys_view_log
            WHERE user_id = #{userId} AND create_time >= #{since}
            """)
    long sumReadSecondsSince(@Param("userId") String userId, @Param("since") java.time.LocalDateTime since);

    /**
     * Song：信任等级用 —— 用户累计访问的不同日期数（全量，用于 sys_user.days_visited 校准）
     */
    @Select("""
            SELECT COUNT(DISTINCT DATE(create_time))
            FROM sys_view_log
            WHERE user_id = #{userId}
            """)
    int countAllDistinctVisitedDays(@Param("userId") String userId);
}
