package com.campus.trend.campus_pulse.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 获取所有正常状态的用户ID（用于布隆过滤器初始化）
     */
    @Select("SELECT id FROM sys_user WHERE status = 1")
    List<String> selectAllActiveUserIds();

    // ── 计数器原子更新：避免"先查再整行回写"的并发丢失更新 ──

    @Update("UPDATE sys_user SET total_likes_received = COALESCE(total_likes_received, 0) + 1 WHERE id = #{userId}")
    int incrementLikesReceived(@Param("userId") String userId);

    @Update("UPDATE sys_user SET total_likes_received = GREATEST(COALESCE(total_likes_received, 0) - 1, 0) WHERE id = #{userId}")
    int decrementLikesReceived(@Param("userId") String userId);

    @Update("UPDATE sys_user SET total_posts = COALESCE(total_posts, 0) + 1 WHERE id = #{userId}")
    int incrementTotalPosts(@Param("userId") String userId);

    @Update("UPDATE sys_user SET likes_given = COALESCE(likes_given, 0) + 1 WHERE id = #{userId}")
    int incrementLikesGiven(@Param("userId") String userId);

    @Update("UPDATE sys_user SET likes_given = GREATEST(COALESCE(likes_given, 0) - 1, 0) WHERE id = #{userId}")
    int decrementLikesGiven(@Param("userId") String userId);

    /** 经验值字段级原子入账，禁止用缓存 User 实体整行回写。 */
    @Update("""
            UPDATE sys_user
               SET experience = GREATEST(COALESCE(experience, 0) + #{delta}, 0),
                   update_time = NOW()
             WHERE id = #{userId}
            """)
    int addExperienceAtomic(@Param("userId") String userId, @Param("delta") int delta);

    @Update("""
            UPDATE sys_user
               SET level = GREATEST(COALESCE(level, 1), #{level}),
                   update_time = NOW()
             WHERE id = #{userId}
            """)
    int raiseLevelAtLeast(@Param("userId") String userId, @Param("level") int level);

    /** 信任等级比较并交换，防止自动重算覆盖管理员刚授予的 TL4。 */
    @Update("""
            UPDATE sys_user
               SET trust_level = #{newLevel},
                   update_time = NOW()
             WHERE id = #{userId}
               AND COALESCE(trust_level, 0) = #{expectedLevel}
            """)
    int updateTrustLevelIfCurrent(@Param("userId") String userId,
                                  @Param("expectedLevel") int expectedLevel,
                                  @Param("newLevel") int newLevel);

    @Update("UPDATE sys_user SET last_active_time = #{activeTime} WHERE id = #{userId}")
    int updateLastActiveTimeAtomic(@Param("userId") String userId,
                                  @Param("activeTime") java.time.LocalDateTime activeTime);

    @Update("UPDATE sys_user SET active_region = #{region} WHERE id = #{userId}")
    int updateActiveRegionAtomic(@Param("userId") String userId, @Param("region") String region);

    @Update("UPDATE sys_user SET contribution_val = GREATEST(COALESCE(contribution_val, 0) + #{amount}, 0) WHERE id = #{userId}")
    int addContribution(@Param("userId") String userId, @Param("amount") int amount);

    // ── 积分原子操作（UserPointsService 专用，余额守卫防并发超扣） ──

    /** 原子扣减：仅当余额足够才成功，返回受影响行数（0=余额不足或用户不存在） */
    @Update("UPDATE sys_user SET points = points - #{amount} WHERE id = #{userId} AND COALESCE(points, 0) >= #{amount}")
    int consumePointsAtomic(@Param("userId") String userId, @Param("amount") int amount);

    /** 原子入账（NULL 视为 0） */
    @Update("UPDATE sys_user SET points = COALESCE(points, 0) + #{amount} WHERE id = #{userId}")
    int creditPointsAtomic(@Param("userId") String userId, @Param("amount") int amount);

    /** 读取当前余额（同事务内在原子更新之后调用即为本笔变动后余额） */
    @Select("SELECT COALESCE(points, 0) FROM sys_user WHERE id = #{userId}")
    Integer selectPointsById(@Param("userId") String userId);

    /** 支付权益顺延的事务级用户锁，串行化同一用户的多笔到账。 */
    @Select("SELECT id FROM sys_user WHERE id = #{userId} FOR UPDATE")
    String lockByIdForUpdate(@Param("userId") String userId);
}

