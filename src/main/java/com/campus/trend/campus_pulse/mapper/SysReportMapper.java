package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.SysReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysReportMapper extends BaseMapper<SysReport> {

    /**
     * Song：聚合某目标的所有未忽略/已处理 flag 权重总和（自治隐藏判定用）
     */
    @Select("""
            SELECT IFNULL(SUM(flag_weight), 0)
            FROM sys_report
            WHERE target_type = #{targetType} AND target_id = #{targetId}
              AND status IN (0, 1)
            """)
    int sumFlagWeight(@Param("targetType") String targetType, @Param("targetId") String targetId);
}

