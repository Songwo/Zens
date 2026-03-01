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
}
