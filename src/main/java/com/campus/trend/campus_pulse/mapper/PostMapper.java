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

    /**
     * 批量更新帖子热度分数
     */
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

    /**
     * 获取所有已发布的帖子ID（用于布隆过滤器初始化）
     */
    @Select("SELECT id FROM sys_post WHERE status = 1 AND (audit_status IS NULL OR audit_status = '' OR audit_status = 'APPROVED')")
    List<String> selectAllPublishedPostIds();

    @Select("SELECT COUNT(*) FROM sys_post WHERE status = 1 AND (audit_status IS NULL OR audit_status = '' OR audit_status = 'APPROVED') AND (FIND_IN_SET(#{tagName}, tags) > 0 OR FIND_IN_SET(#{tagNameSpaced}, tags) > 0 OR tags = #{tagName})")
    int countByTagName(@Param("tagName") String tagName, @Param("tagNameSpaced") String tagNameSpaced);

    @Update("UPDATE sys_post SET collect_count = COALESCE(collect_count, 0) + 1, update_time = NOW() WHERE id = #{postId}")
    int incrementCollectCount(@Param("postId") String postId);

    @Update("UPDATE sys_post SET collect_count = GREATEST(COALESCE(collect_count, 0) - 1, 0), update_time = NOW() WHERE id = #{postId}")
    int decrementCollectCount(@Param("postId") String postId);

    @Update("UPDATE sys_post SET like_count = COALESCE(like_count, 0) + 1, update_time = NOW() WHERE id = #{postId}")
    int incrementLikeCount(@Param("postId") String postId);

    @Update("UPDATE sys_post SET like_count = GREATEST(COALESCE(like_count, 0) - 1, 0), update_time = NOW() WHERE id = #{postId}")
    int decrementLikeCount(@Param("postId") String postId);

    /**
     * 评论数 +1 并刷新最后回复/活跃时间（原子更新，避免整行回写丢并发更新）
     */
    @Update("UPDATE sys_post SET comment_count = COALESCE(comment_count, 0) + 1, last_reply_at = #{now}, last_activity_at = #{now}, update_time = NOW() WHERE id = #{postId}")
    int incrementCommentCount(@Param("postId") String postId, @Param("now") LocalDateTime now);

    @Update("UPDATE sys_post SET comment_count = GREATEST(COALESCE(comment_count, 0) - 1, 0), update_time = NOW() WHERE id = #{postId}")
    int decrementCommentCount(@Param("postId") String postId);

    @Select("""
            <script>
            SELECT
                section_id AS sectionId,
                COUNT(*) AS postCount,
                SUM(CASE WHEN create_time <![CDATA[ >= ]]> #{todayStart} THEN 1 ELSE 0 END) AS todayCount,
                COALESCE(ROUND(SUM(COALESCE(heat_score, 0))), 0) AS heatScore
            FROM sys_post
            WHERE status = 1
              AND (audit_status IS NULL OR audit_status = '' OR audit_status = 'APPROVED')
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
