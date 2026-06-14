package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.TrustEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TrustEventMapper extends BaseMapper<TrustEvent> {

    /**
     * 用户最近一次变更事件
     */
    @Select("SELECT * FROM sys_trust_event WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT 1")
    TrustEvent selectLatestByUserId(@Param("userId") String userId);
}
