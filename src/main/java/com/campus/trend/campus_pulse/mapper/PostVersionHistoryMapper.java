package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.PostVersionHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PostVersionHistoryMapper extends BaseMapper<PostVersionHistory> {

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM post_version_history WHERE post_id = #{postId}")
    Integer selectMaxVersionNo(@Param("postId") String postId);
}
