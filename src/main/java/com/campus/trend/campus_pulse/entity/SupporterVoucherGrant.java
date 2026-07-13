package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("supporter_voucher_grant")
public class SupporterVoucherGrant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String sourceOrderNo;
    private String planCode;
    private Integer quota;
    private String status;
    private Long voucherCodeId;
    private String redemptionUrlSnapshot;
    private LocalDateTime grantedAt;
    private LocalDateTime issuedAt;
}
