package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.dto.request.PostHeatUpdateItem;
import com.campus.trend.campus_pulse.dto.response.SectionStatsAggResp;
import com.campus.trend.campus_pulse.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    @Update("""
            <script>
            UPDATE sys_post
            SET heat_score = CASE id
            <foreach collection="updates" item="item">
                WHEN #{item.id} THEN #{item.heatScore}
            </foreach>
            END,
            update_time = NOW()
            WHERE id IN
            <foreach collection="updates" item="item" open="(" separator="," close=")">
                #{item.id}
            </foreach>
            </script>
            """)
    int batchUpdateHeatScores(@Param("updates") List<PostHeatUpdateItem> updates);

    @Select("SELECT COUNT(*) FROM sys_post WHERE status = 1 AND (FIND_IN_SET(#{tagName}, tags) > 0 OR FIND_IN_SET(#{tagNameSpaced}, tags) > 0 OR tags = #{tagName})")
    int countByTagName(@Param("tagName") String tagName, @Param("tagNameSpaced") String tagNameSpaced);

    @Select("""
            <script>
            SELECT
                section_id AS sectionId,
                COUNT(*) AS postCount,
                SUM(CASE WHEN create_time <![CDATA[ >= ]]> #{todayStart} THEN 1 ELSE 0 END) AS todayCount,
                COALESCE(ROUND(SUM(COALESCE(heat_score, 0))), 0) AS heatScore
            FROM sys_post
            WHERE status = 1
              AND section_id IN
              <foreach collection="sectionIds" item="sectionId" open="(" separator="," close=")">
                  #{sectionId}
              </foreach>
            GROUP BY section_id
            </script>
            """)
    List<SectionStatsAggResp> aggregateSectionStats(@Param("sectionIds") List<Long> sectionIds,
                                                    @Param("todayStart") LocalDateTime todayStart);
}
