package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.LevelExpLog;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.LevelExpLogMapper;
import com.campus.trend.campus_pulse.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 内部 s2s API,只服务 Zens 子站(如 zdc-shop)。
 * 鉴权由 InternalServiceFilter 统一完成,本控制器不再校验。
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/user")
@RequiredArgsConstructor
public class InternalUserController {

    private static final long IDEMPOTENT_TTL_SECONDS = 120;
    private static final int MAX_CONSUME_AMOUNT = 1_000_000;

    private final UserService userService;
    private final LevelExpLogMapper levelExpLogMapper;
    private final StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * GET /api/internal/user/{userId}/points
     * 返回该用户的当前积分余额。
     */
    @GetMapping("/{userId}/points")
    public Result<?> getPoints(@PathVariable @NotBlank String userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.failed("USER_NOT_FOUND: 用户不存在");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("username", user.getUsername());
        body.put("points", user.getPoints() == null ? 0 : user.getPoints());
        body.put("level", user.getLevel());
        return Result.success(body);
    }

    /**
     * POST /api/internal/user/{userId}/points/consume
     *
     * 原子扣减积分,失败回 INSUFFICIENT_POINTS。
     * 幂等性: 相同 idempotencyKey 在 TTL 内返回上次结果。
     */
    @PostMapping("/{userId}/points/consume")
    public Result<?> consumePoints(@PathVariable @NotBlank String userId,
                                   @RequestBody @Valid ConsumeRequest req) {
        if (req.amount > MAX_CONSUME_AMOUNT) {
            return Result.failed("INVALID_AMOUNT: 单次扣减不得超过 " + MAX_CONSUME_AMOUNT);
        }

        String idempotentKey = "auth:internal:idem:" + userId + ":" + req.idempotencyKey;
        String cached = redisTemplate.opsForValue().get(idempotentKey);
        if (cached != null) {
            log.info("[internal/consume] 命中幂等缓存: userId={}, key={}", userId, req.idempotencyKey);
            try {
                Map<?, ?> data = objectMapper.readValue(cached, Map.class);
                return Result.success(data);
            } catch (Exception e) {
                log.warn("[internal/consume] 幂等缓存反序列化失败,落回正常路径", e);
            }
        }

        User user = userService.getById(userId);
        if (user == null) {
            return Result.failed("USER_NOT_FOUND: 用户不存在");
        }
        int before = user.getPoints() == null ? 0 : user.getPoints();
        if (before < req.amount) {
            return Result.failed("INSUFFICIENT_POINTS: 积分不足 (当前 " + before + " < 需要 " + req.amount + ")");
        }

        // 原子扣减: 仅在 points >= amount 时才更新成功
        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .ge(User::getPoints, req.amount)
                .setSql("points = points - " + req.amount);
        boolean updated = userService.update(uw);
        if (!updated) {
            return Result.failed("INSUFFICIENT_POINTS: 余额不足或并发扣减失败");
        }

        // 记一条流水到 sys_level_exp_log (字段含义为通用积分变更日志, expDelta 正负皆可)
        try {
            LevelExpLog logEntry = new LevelExpLog()
                    .setUserId(userId)
                    .setExpDelta(-req.amount)
                    .setReason("shop:consume:" + req.reason + ":" + req.orderId)
                    .setCreateTime(LocalDateTime.now());
            levelExpLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("[internal/consume] 写流水失败但不阻断: userId={}, orderId={}", userId, req.orderId, e);
        }

        int after = before - req.amount;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("pointsAfter", after);
        result.put("consumedAmount", req.amount);
        result.put("reason", req.reason);
        result.put("orderId", req.orderId);
        result.put("idempotencyKey", req.idempotencyKey);

        try {
            redisTemplate.opsForValue().set(
                    idempotentKey, objectMapper.writeValueAsString(result),
                    IDEMPOTENT_TTL_SECONDS, TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("[internal/consume] 幂等结果回写 Redis 失败", e);
        }

        log.info("[internal/consume] OK userId={} amount={} before={} after={} orderId={}",
                userId, req.amount, before, after, req.orderId);
        return Result.success(result);
    }

    /**
     * POST /api/internal/user/{userId}/points/credit
     *
     * 返还积分 (退款 / 补偿 / 奖励发放)。
     * 幂等性: 相同 idempotencyKey 在 TTL 内返回上次结果。
     */
    @PostMapping("/{userId}/points/credit")
    public Result<?> creditPoints(@PathVariable @NotBlank String userId,
                                  @RequestBody @Valid ConsumeRequest req) {
        if (req.amount > MAX_CONSUME_AMOUNT) {
            return Result.failed("INVALID_AMOUNT: 单次充值不得超过 " + MAX_CONSUME_AMOUNT);
        }

        String idempotentKey = "auth:internal:idem:credit:" + userId + ":" + req.idempotencyKey;
        String cached = redisTemplate.opsForValue().get(idempotentKey);
        if (cached != null) {
            try {
                Map<?, ?> data = objectMapper.readValue(cached, Map.class);
                return Result.success(data);
            } catch (Exception e) {
                log.warn("[internal/credit] 幂等缓存反序列化失败,落回正常路径", e);
            }
        }

        User user = userService.getById(userId);
        if (user == null) {
            return Result.failed("USER_NOT_FOUND: 用户不存在");
        }
        int before = user.getPoints() == null ? 0 : user.getPoints();

        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("points = COALESCE(points, 0) + " + req.amount);
        boolean updated = userService.update(uw);
        if (!updated) {
            return Result.failed("CREDIT_FAIL: 充值失败");
        }

        try {
            LevelExpLog logEntry = new LevelExpLog()
                    .setUserId(userId)
                    .setExpDelta(req.amount)
                    .setReason("shop:credit:" + req.reason + ":" + req.orderId)
                    .setCreateTime(LocalDateTime.now());
            levelExpLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("[internal/credit] 写流水失败但不阻断", e);
        }

        int after = before + req.amount;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("pointsAfter", after);
        result.put("creditedAmount", req.amount);
        result.put("reason", req.reason);
        result.put("orderId", req.orderId);
        result.put("idempotencyKey", req.idempotencyKey);

        try {
            redisTemplate.opsForValue().set(
                    idempotentKey, objectMapper.writeValueAsString(result),
                    IDEMPOTENT_TTL_SECONDS, TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("[internal/credit] 幂等结果回写 Redis 失败", e);
        }

        log.info("[internal/credit] OK userId={} amount={} before={} after={} orderId={}",
                userId, req.amount, before, after, req.orderId);
        return Result.success(result);
    }

    // ─── DTO ────────────────────────────────────────────────────────────
    public static class ConsumeRequest {
        @Min(value = 1, message = "amount 必须为正整数")
        public int amount;
        @NotBlank @Size(max = 200)
        public String reason;
        @NotBlank @Size(max = 100)
        public String orderId;
        @NotBlank @Size(min = 8, max = 100)
        public String idempotencyKey;
    }
}
