package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.PointSummaryResp;
import com.campus.trend.campus_pulse.dto.response.SimplePageResp;
import com.campus.trend.campus_pulse.dto.response.PointTxnResp;

import java.util.Map;

/**
 * 积分钱包统一收口：站内赚分与子站消费/返还全部经此进出,
 * 每笔变动在同一事务内原子更新余额并写入 sys_point_txn 账本。
 *
 * 幂等:DB 唯一索引(uk_point_txn_idem)是最终裁判,Redis 缓存仅为快路径;
 * 重复幂等键返回首次结果,绝不重复变动。
 *
 * 失败统一抛 BusinessException(ResultCode.FAILED, "前缀: 说明")，
 * 错误消息前缀（USER_NOT_FOUND / INSUFFICIENT_POINTS / INVALID_AMOUNT / CREDIT_FAIL）
 * 是子站客户端的解析契约，不可改动。
 */
public interface UserPointsService {

    /** 查询积分余额 */
    Map<String, Object> getPoints(String userId);

    /**
     * 原子扣减积分(子站 s2s)。
     * @param source 调用方 serviceId(InternalServiceFilter 注入),落账本 source 列
     */
    Map<String, Object> consume(String userId, int amount, String reason, String orderId,
                                String idempotencyKey, String source);

    /**
     * 返还/发放积分(子站 s2s)。
     * @param source 调用方 serviceId(InternalServiceFilter 注入),落账本 source 列
     */
    Map<String, Object> credit(String userId, int amount, String reason, String orderId,
                               String idempotencyKey, String source);

    /**
     * 站内赚分(签到等,source=main-site)。
     * 幂等键 = earn:{bizType}:{userId}:{bizKey},同一业务键天然防重复入账。
     *
     * @param bizType 业务类型,如 checkin
     * @param bizKey  业务幂等要素,如签到日期 2026-07-07
     */
    Map<String, Object> earn(String userId, int amount, String bizType, String bizKey, String reason);

    /** 分页查询积分流水(登录用户查自己的账本明细) */
    SimplePageResp<PointTxnResp> pageTransactions(String userId, int page, int pageSize);

    /** 当前余额 + 本月收支合计 */
    PointSummaryResp getSummary(String userId);
}
