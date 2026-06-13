package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 全量校准 post_count 冗余列(口径与原 countByTagName 一致)。
     * 离线定时执行,代替标签搜索时的逐标签 FIND_IN_SET 实时 COUNT。
     */
    @Update("""
            UPDATE sys_tag t
            SET t.post_count = (
                SELECT COUNT(*)
                FROM sys_post p
                WHERE p.status = 1
                  AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'PENDING' OR p.audit_status = 'APPROVED')
                  AND (FIND_IN_SET(t.name, p.tags) > 0
                       OR FIND_IN_SET(CONCAT(' ', t.name), p.tags) > 0
                       OR p.tags = t.name)
            )
            """)
    int recalculateAllPostCounts();
}
