package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    @Update("UPDATE sys_comment SET like_count = COALESCE(like_count, 0) + 1 WHERE id = #{commentId}")
    int incrementLikeCount(@Param("commentId") String commentId);

    @Update("UPDATE sys_comment SET like_count = GREATEST(COALESCE(like_count, 0) - 1, 0) WHERE id = #{commentId}")
    int decrementLikeCount(@Param("commentId") String commentId);
}
