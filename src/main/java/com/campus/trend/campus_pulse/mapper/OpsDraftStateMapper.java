package com.campus.trend.campus_pulse.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OpsDraftStateMapper {
  @Update(
      "UPDATE ops_draft SET status = 'PUBLISHING', update_time = NOW() WHERE id = #{id} AND status"
          + " = 'APPROVED'")
  int claimForPublish(@Param("id") String id);
}
