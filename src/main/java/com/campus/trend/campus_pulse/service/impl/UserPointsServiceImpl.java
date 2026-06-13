package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.entity.LevelExpLog;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.LevelExpLogMapper;
import com.campus.trend.campus_pulse.service.UserPointsService;
import com.campus.trend.campus_pulse.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPointsServiceImpl implements UserPointsService {

    private static final long IDEMPOTENT_TTL_SECONDS = 120;
    private static final int MAX_CONSUME_AMOUNT = 1_000_000;

    private final UserService userService;
    private final LevelExpLogMapper levelExpLogMapper;
    private final StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
    public Map<String, Object> consume(String userId, int amount, String reason, String orderId, String idempotencyKey) {
        if (amount > MAX_CONSUME_AMOUNT) {
            throw new BusinessException(ResultCode.FAILED, "INVALID_AMOUNT: 单次扣减不得超过 " + MAX_CONSUME_AMOUNT);
        }

        String idempotentKey = "auth:internal:idem:" + userId + ":" + idempotencyKey;
        Map<String, Object> cached = readIdempotentCache(idempotentKey, "[internal/consume]");
        if (cached != null) {
            log.info("[internal/consume] 命中幂等缓存: userId={}, key={}", userId, idempotencyKey);
            return cached;
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.FAILED, "USER_NOT_FOUND: 用户不存在");
        }
        int before = user.getPoints() == null ? 0 : user.getPoints();
        if (before < amount) {
            throw new BusinessException(ResultCode.FAILED,
                    "INSUFFICIENT_POINTS: 积分不足 (当前 " + before + " < 需要 " + amount + ")");
        }

        // 原子扣减: 仅在 points >= amount 时才更新成功
        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .ge(User::getPoints, amount)
                .setSql("points = points - " + amount);
        boolean updated = userService.update(uw);
        if (!updated) {
            throw new BusinessException(ResultCode.FAILED, "INSUFFICIENT_POINTS: 余额不足或并发扣减失败");
        }

        writeExpLog(userId, -amount, "shop:consume:" + reason + ":" + orderId, "[internal/consume]", orderId);

        int after = before - amount;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("pointsAfter", after);
        result.put("consumedAmount", amount);
        result.put("reason", reason);
        result.put("orderId", orderId);
        result.put("idempotencyKey", idempotencyKey);

        writeIdempotentCache(idempotentKey, result, "[internal/consume]");

        log.info("[internal/consume] OK userId={} amount={} before={} after={} orderId={}",
                userId, amount, before, after, orderId);
        return result;
    }

    @Override
    public Map<String, Object> credit(String userId, int amount, String reason, String orderId, String idempotencyKey) {
        if (amount > MAX_CONSUME_AMOUNT) {
            throw new BusinessException(ResultCode.FAILED, "INVALID_AMOUNT: 单次充值不得超过 " + MAX_CONSUME_AMOUNT);
        }

        String idempotentKey = "auth:internal:idem:credit:" + userId + ":" + idempotencyKey;
        Map<String, Object> cached = readIdempotentCache(idempotentKey, "[internal/credit]");
        if (cached != null) {
            return cached;
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.FAILED, "USER_NOT_FOUND: 用户不存在");
        }
        int before = user.getPoints() == null ? 0 : user.getPoints();

        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("points = COALESCE(points, 0) + " + amount);
        boolean updated = userService.update(uw);
        if (!updated) {
            throw new BusinessException(ResultCode.FAILED, "CREDIT_FAIL: 充值失败");
        }

        writeExpLog(userId, amount, "shop:credit:" + reason + ":" + orderId, "[internal/credit]", orderId);

        int after = before + amount;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("pointsAfter", after);
        result.put("creditedAmount", amount);
        result.put("reason", reason);
        result.put("orderId", orderId);
        result.put("idempotencyKey", idempotencyKey);

        writeIdempotentCache(idempotentKey, result, "[internal/credit]");

        log.info("[internal/credit] OK userId={} amount={} before={} after={} orderId={}",
                userId, amount, before, after, orderId);
        return result;
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
            log.warn("{} 幂等缓存反序列化失败,落回正常路径", logTag, e);
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
            log.warn("{} 幂等结果回写 Redis 失败", logTag, e);
        }
    }

    private void writeExpLog(String userId, int expDelta, String reason, String logTag, String orderId) {
        try {
            LevelExpLog logEntry = new LevelExpLog()
                    .setUserId(userId)
                    .setExpDelta(expDelta)
                    .setReason(reason)
                    .setCreateTime(LocalDateTime.now());
            levelExpLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("{} 写流水失败但不阻断: userId={}, orderId={}", logTag, userId, orderId, e);
        }
    }
}
