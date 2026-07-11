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
@TableName("payment_order")
public class PaymentOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String userId;
    private String planCode;
    private String planNameSnapshot;
    private Integer amountCents;
    private String currency;
    private String provider;
    private String providerOrderNo;
    private String status;
    private String idempotencyKey;
    private String checkoutUrl;
    private String failureReason;
    private LocalDateTime paidAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
