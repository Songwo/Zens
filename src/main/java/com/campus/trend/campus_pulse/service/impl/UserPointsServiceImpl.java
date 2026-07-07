package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.PointSummaryResp;
import com.campus.trend.campus_pulse.dto.response.PointTxnResp;
import com.campus.trend.campus_pulse.dto.response.SimplePageResp;
import com.campus.trend.campus_pulse.entity.PointTxn;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.PointTxnMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.UserPointsService;
import com.campus.trend.campus_pulse.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 积分钱包唯一收口。
 *
 * 一致性模型:
 *  - 每笔变动 = 同一 DB 事务内「原子 UPDATE 余额 + INSERT sys_point_txn 账本」,余额与流水必然一致;
 *  - 幂等最终裁判 = 账本 idempotency_key 唯一索引:重复键触发回滚后按首次流水重放结果;
 *  - Redis 幂等缓存仅为快路径,丢失不影响正确性(此前仅靠 Redis,7 天 TTL 后可重复扣减)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPointsServiceImpl implements UserPointsService {

    private static final long IDEMPOTENT_TTL_SECONDS = TimeUnit.DAYS.toSeconds(7);
    private static final int MAX_MUTATION_AMOUNT = 1_000_000;
    private static final String SOURCE_MAIN_SITE = "main-site";

    private final UserService userService;
    private final UserMapper userMapper;
    private final PointTxnMapper pointTxnMapper;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.campus.trend.campus_pulse.monitor.EcosystemMetrics ecosystemMetrics;

    @Override
    public Map<String, Object> getPoints(String userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.FAILED, "USER_NOT_FOUND: 用户不存在");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("username", user.getUsername());
        body.put("points", user.getPoints() == null ? 0 : user.getPoints());
        body.put("level", user.getLevel());
        return body;
    }

    @Override
    public Map<String, Object> consume(String userId, int amount, String reason, String orderId,
                                       String idempotencyKey, String source) {
        return mutate("consume", userId, -amount, reason, orderId,
                "consume:" + userId + ":" + idempotencyKey,
                "auth:internal:idem:" + userId + ":" + idempotencyKey,
                normalizeSource(source), deriveBizType(source, "consume"), idempotencyKey);
    }

    @Override
    public Map<String, Object> credit(String userId, int amount, String reason, String orderId,
                                      String idempotencyKey, String source) {
        return mutate("credit", userId, amount, reason, orderId,
                "credit:" + userId + ":" + idempotencyKey,
                "auth:internal:idem:credit:" + userId + ":" + idempotencyKey,
                normalizeSource(source), deriveBizType(source, "credit"), idempotencyKey);
    }

    @Override
    public Map<String, Object> earn(String userId, int amount, String bizType, String bizKey, String reason) {
        String scopedKey = "earn:" + bizType + ":" + userId + ":" + bizKey;
        return mutate("earn", userId, amount, reason, null,
                scopedKey,
                "auth:internal:idem:" + scopedKey,
                SOURCE_MAIN_SITE, bizType, bizKey);
    }

    /**
     * 统一变动入口。
     *
     * @param op          consume / credit / earn (指标与结果字段命名)
     * @param delta       正=入账 负=扣减
     * @param scopedKey   作用域化幂等键(落账本唯一索引)
     * @param redisKey    幂等快路径缓存键(与历史键格式兼容)
     * @param clientKey   调用方原始幂等键(回显给调用方)
     */
    private Map<String, Object> mutate(String op, String userId, int delta, String reason, String orderId,
                                       String scopedKey, String redisKey,
                                       String source, String bizType, String clientKey) {
        int amount = Math.abs(delta);
        if (amount == 0 || amount > MAX_MUTATION_AMOUNT) {
            throw new BusinessException(ResultCode.FAILED, "INVALID_AMOUNT: 单次变动必须在 1~" + MAX_MUTATION_AMOUNT + " 之间");
        }

        // 1) Redis 快路径
        Map<String, Object> cached = readIdempotentCache(redisKey, op);
        if (cached != null) {
            log.info("[points/{}] 命中幂等缓存(redis): userId={}, key={}", op, userId, scopedKey);
            return cached;
        }

        // 2) DB 权威幂等检查(Redis 丢失后的兜底)
        PointTxn existing = findByIdemKey(scopedKey);
        if (existing != null) {
            log.info("[points/{}] 命中幂等流水(db): userId={}, key={}", op, userId, scopedKey);
            Map<String, Object> replay = buildResult(op, existing, clientKey);
            writeIdempotentCache(redisKey, replay, op);
            return replay;
        }

        User user = userService.getById(userId);
        if (user == null) {
            ecosystemMetrics.recordPointsMutation(op, "not_found");
            throw new BusinessException(ResultCode.FAILED, "USER_NOT_FOUND: 用户不存在");
        }
        int before = user.getPoints() == null ? 0 : user.getPoints();
        if (delta < 0 && before < amount) {
            ecosystemMetrics.recordPointsMutation(op, "insufficient");
            throw new BusinessException(ResultCode.FAILED,
                    "INSUFFICIENT_POINTS: 积分不足 (当前 " + before + " < 需要 " + amount + ")");
        }

        // 3) 事务内:原子更新余额 + 写账本。撞唯一索引 → 整体回滚 → 按首次流水重放。
        PointTxn txn;
        try {
            txn = transactionTemplate.execute(status -> {
                int updated = delta < 0
                        ? userMapper.consumePointsAtomic(userId, amount)
                        : userMapper.creditPointsAtomic(userId, amount);
                if (updated != 1) {
                    // 扣减:余额守卫拦下(并发耗尽);入账:用户不存在
                    throw new BusinessException(ResultCode.FAILED, delta < 0
                            ? "INSUFFICIENT_POINTS: 余额不足或并发扣减失败"
                            : "CREDIT_FAIL: 入账失败");
                }
                Integer balanceAfter = userMapper.selectPointsById(userId);
                if (balanceAfter == null) {
                    throw new BusinessException(ResultCode.FAILED, "USER_NOT_FOUND: 用户不存在");
                }
                PointTxn row = PointTxn.builder()
                        .userId(userId)
                        .delta(delta)
                        .balanceAfter(balanceAfter)
                        .source(source)
                        .bizType(bizType)
                        .orderId(orderId)
                        .reason(reason)
                        .idempotencyKey(scopedKey)
                        .createdAt(LocalDateTime.now())
                        .build();
                pointTxnMapper.insert(row);
                return row;
            });
        } catch (DuplicateKeyException e) {
            // 并发同键请求:另一笔已落账,回滚本笔后按首次结果重放
            PointTxn winner = findByIdemKey(scopedKey);
            if (winner == null) {
                log.error("[points/{}] 幂等键冲突但查不到流水: key={}", op, scopedKey, e);
                throw new BusinessException(ResultCode.FAILED, "POINTS_CONFLICT: 积分变动冲突,请重试");
            }
            Map<String, Object> replay = buildResult(op, winner, clientKey);
            writeIdempotentCache(redisKey, replay, op);
            ecosystemMetrics.recordPointsMutation(op, "idem_replay");
            return replay;
        } catch (BusinessException e) {
            ecosystemMetrics.recordPointsMutation(op, delta < 0 ? "insufficient" : "error");
            throw e;
        }

        Map<String, Object> result = buildResult(op, txn, clientKey);
        writeIdempotentCache(redisKey, result, op);
        ecosystemMetrics.recordPointsMutation(op, "success");
        log.info("[points/{}] OK userId={} delta={} after={} source={} biz={} orderId={}",
                op, userId, delta, txn.getBalanceAfter(), source, bizType, orderId);
        return result;
    }

    /** 由流水重建响应(首次与幂等重放共用,保证两条路径响应一致) */
    private Map<String, Object> buildResult(String op, PointTxn txn, String clientKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", txn.getUserId());
        result.put("pointsAfter", txn.getBalanceAfter());
        switch (op) {
            case "consume" -> result.put("consumedAmount", Math.abs(txn.getDelta()));
            case "credit" -> result.put("creditedAmount", txn.getDelta());
            default -> result.put("earnedAmount", txn.getDelta());
        }
        result.put("reason", txn.getReason());
        if (txn.getOrderId() != null) {
            result.put("orderId", txn.getOrderId());
        }
        result.put("idempotencyKey", clientKey);
        return result;
    }

    private PointTxn findByIdemKey(String scopedKey) {
        return pointTxnMapper.selectOne(new LambdaQueryWrapper<PointTxn>()
                .eq(PointTxn::getIdempotencyKey, scopedKey)
                .last("LIMIT 1"));
    }

    /** 子站语义映射:消费/退款的业务类型按来源站命名 */
    private static String deriveBizType(String source, String op) {
        String s = source == null ? "" : source;
        return switch (s) {
            case "zdc-shop" -> "consume".equals(op) ? "shop.order" : "shop.refund";
            case "campus-lottery-station" -> "consume".equals(op) ? "lottery.join" : "lottery.refund";
            default -> (s.isEmpty() ? "internal" : s) + "." + op;
        };
    }

    private static String normalizeSource(String source) {
        return source == null || source.isBlank() ? "internal" : source;
    }

    @Override
    public SimplePageResp<PointTxnResp> pageTransactions(String userId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<PointTxn> queryPage = new Page<>(safePage, safePageSize);
        Page<PointTxn> result = pointTxnMapper.selectPage(queryPage, new LambdaQueryWrapper<PointTxn>()
                .eq(PointTxn::getUserId, userId)
                .orderByDesc(PointTxn::getCreatedAt)
                .orderByDesc(PointTxn::getId));
        List<PointTxnResp> records = result.getRecords().stream()
                .map(this::toResp)
                .toList();
        return SimplePageResp.of(records, result.getTotal());
    }

    @Override
    public PointSummaryResp getSummary(String userId) {
        User user = userService.getById(userId);
        int points = user != null && user.getPoints() != null ? user.getPoints() : 0;

        LocalDateTime monthStart = LocalDateTime.now()
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        List<PointTxn> monthTxns = pointTxnMapper.selectList(new LambdaQueryWrapper<PointTxn>()
                .eq(PointTxn::getUserId, userId)
                .ge(PointTxn::getCreatedAt, monthStart));
        int monthEarned = 0;
        int monthSpent = 0;
        for (PointTxn txn : monthTxns) {
            int delta = txn.getDelta() == null ? 0 : txn.getDelta();
            if (delta > 0) {
                monthEarned += delta;
            } else {
                monthSpent += -delta;
            }
        }
        return PointSummaryResp.builder()
                .points(points)
                .monthEarned(monthEarned)
                .monthSpent(monthSpent)
                .build();
    }

    private PointTxnResp toResp(PointTxn txn) {
        return PointTxnResp.builder()
                .id(txn.getId())
                .delta(txn.getDelta())
                .balanceAfter(txn.getBalanceAfter())
                .source(txn.getSource())
                .bizType(txn.getBizType())
                .orderId(txn.getOrderId())
                .reason(txn.getReason())
                .createdAt(txn.getCreatedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readIdempotentCache(String key, String logTag) {
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, Map.class);
        } catch (Exception e) {
            log.warn("[points/{}] 幂等缓存反序列化失败,落回 DB 路径", logTag, e);
            return null;
        }
    }

    private void writeIdempotentCache(String key, Map<String, Object> result, String logTag) {
        try {
            redisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(result),
                    IDEMPOTENT_TTL_SECONDS, TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("[points/{}] 幂等结果回写 Redis 失败(不影响正确性,DB 唯一索引兜底)", logTag, e);
        }
    }
}
