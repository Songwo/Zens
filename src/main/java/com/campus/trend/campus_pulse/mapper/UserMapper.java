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
}

