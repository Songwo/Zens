package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.PollVote;
import org.apache.ibatis.annotations.Mapper;

/**
 * 投票记录Mapper
 */
@Mapper
public interface PollVoteMapper extends BaseMapper<PollVote> {
}
