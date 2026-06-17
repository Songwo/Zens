package com.campus.trend.campus_pulse.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 获取所有正常状态的用户ID（用于布隆过滤器初始化）
     */
    @Select("SELECT id FROM sys_user WHERE status = 1")
    List<String> selectAllActiveUserIds();
}

