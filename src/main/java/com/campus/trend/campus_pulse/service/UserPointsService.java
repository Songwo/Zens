package com.campus.trend.campus_pulse.service;

import java.util.Map;

/**
 * 内部积分钱包（s2s,供 zdc-shop 等子站经 InternalServiceFilter 调用）。
 * 失败统一抛 BusinessException(ResultCode.FAILED, "前缀: 说明")，
 * 错误消息前缀（USER_NOT_FOUND / INSUFFICIENT_POINTS / INVALID_AMOUNT / CREDIT_FAIL）
 * 是子站客户端的解析契约，不可改动。
 */
public interface UserPointsService {

    /** 查询积分余额 */
    Map<String, Object> getPoints(String userId);

    /** 原子扣减积分，幂等键 TTL 内重复请求返回上次结果 */
    Map<String, Object> consume(String userId, int amount, String reason, String orderId, String idempotencyKey);

    /** 返还/发放积分，幂等键 TTL 内重复请求返回上次结果 */
    Map<String, Object> credit(String userId, int amount, String reason, String orderId, String idempotencyKey);
}
