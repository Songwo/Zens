package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.SupporterVoucherCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SupporterVoucherCodeMapper extends BaseMapper<SupporterVoucherCode> {
    @Select("""
            SELECT * FROM supporter_voucher_code
            WHERE quota = #{quota} AND status = 'AVAILABLE'
            ORDER BY id ASC LIMIT 1 FOR UPDATE
            """)
    SupporterVoucherCode lockOldestAvailable(@Param("quota") int quota);

    @Update("""
            UPDATE supporter_voucher_code
            SET status = 'ASSIGNED', assigned_grant_id = #{grantId}, assigned_at = #{assignedAt}
            WHERE id = #{codeId} AND status = 'AVAILABLE'
            """)
    int assignIfAvailable(@Param("codeId") Long codeId, @Param("grantId") Long grantId,
                          @Param("assignedAt") LocalDateTime assignedAt);
}
