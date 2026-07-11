package com.campus.trend.campus_pulse.seo;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SeoPostMapper {

    @Select("""
            SELECT id, title, summary, content, cover_image AS coverImage,
                   create_time AS createTime, update_time AS updateTime
            FROM sys_post
            WHERE status = 1
              AND (audit_status IS NULL OR audit_status = '' OR audit_status = 'APPROVED')
            ORDER BY COALESCE(update_time, create_time) DESC
            LIMIT 50000
            """)
    List<SeoPostDocument> selectPublishedPosts();
}
