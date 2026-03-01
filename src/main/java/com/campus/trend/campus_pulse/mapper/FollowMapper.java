package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.Follow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 关注表Mapper
 */
@Mapper
public interface FollowMapper extends BaseMapper<Follow> {

    /**
     * 获取用户的关注列表
     */
    @Select("SELECT followee_id FROM follows WHERE follower_id = #{userId}")
    List<String> selectFollowingUserIds(@Param("userId") String userId);

    /**
     * 获取用户的粉丝列表
     */
    @Select("SELECT follower_id FROM follows WHERE followee_id = #{userId}")
    List<String> selectFollowerUserIds(@Param("userId") String userId);

    /**
     * 检查是否已关注
     */
    @Select("SELECT COUNT(*) FROM follows WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
    int checkFollowExists(@Param("followerId") String followerId, @Param("followeeId") String followeeId);
}
