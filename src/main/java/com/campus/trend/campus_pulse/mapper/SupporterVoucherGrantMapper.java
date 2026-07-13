package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.SupporterVoucherGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SupporterVoucherGrantMapper extends BaseMapper<SupporterVoucherGrant> {
    @Select("SELECT * FROM supporter_voucher_grant WHERE source_order_no = #{orderNo} LIMIT 1 FOR UPDATE")
    SupporterVoucherGrant lockBySourceOrderNo(@Param("orderNo") String orderNo);

    @Select("""
            SELECT * FROM supporter_voucher_grant
            WHERE quota = #{quota} AND status = 'PENDING'
            ORDER BY granted_at ASC, id ASC LIMIT 1 FOR UPDATE
            """)
    SupporterVoucherGrant lockOldestPending(@Param("quota") int quota);

    @Update("""
            UPDATE supporter_voucher_grant
            SET status = 'ISSUED', voucher_code_id = #{codeId}, issued_at = #{issuedAt}
            WHERE id = #{grantId} AND status = 'PENDING'
            """)
    int issueIfPending(@Param("grantId") Long grantId, @Param("codeId") Long codeId,
                       @Param("issuedAt") LocalDateTime issuedAt);
}
