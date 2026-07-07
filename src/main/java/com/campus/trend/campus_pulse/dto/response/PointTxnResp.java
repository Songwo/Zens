package com.campus.trend.campus_pulse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 积分流水明细响应,对应 sys_point_txn 一行。 */
@Data
@Builder
public class PointTxnResp {
    private Long id;
    private Integer delta;
    private Integer balanceAfter;
    private String source;
    private String bizType;
    private String orderId;
    private String reason;
    private LocalDateTime createdAt;
}
