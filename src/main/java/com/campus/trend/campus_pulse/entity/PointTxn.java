package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 积分流水账本：主站及全部子站的积分变动唯一审计源。
 * 幂等最终裁判是 idempotency_key 上的唯一索引(uk_point_txn_idem)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_point_txn")
public class PointTxn {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    /** 积分变动,正=入账 负=扣减 */
    private Integer delta;

    /** 本笔完成后的余额 */
    private Integer balanceAfter;

    /** 来源: main-site / zdc-shop / campus-lottery-station / cdk-airdrop */
    private String source;

    /** 业务类型: checkin / shop.order / shop.refund / lottery.join / lottery.refund / admin.adjust */
    private String bizType;

    /** 子站业务单号 */
    private String orderId;

    private String reason;

    /** 作用域化幂等键,如 consume:{userId}:{clientKey} */
    private String idempotencyKey;

    private LocalDateTime createdAt;
}
