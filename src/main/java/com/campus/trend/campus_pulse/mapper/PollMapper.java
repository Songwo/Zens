package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.Poll;
import org.apache.ibatis.annotations.Mapper;

/**
 * 帖子投票Mapper
 */
@Mapper
public interface PollMapper extends BaseMapper<Poll> {
}
