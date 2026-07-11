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
@TableName("payment_callback_event")
public class PaymentCallbackEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String provider;
    private String eventId;
    private String orderNo;
    private String payloadHash;
    private Boolean signatureValid;
    private String status;
    private String errorMessage;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
}
