package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.notification.NotificationType;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.UserPointsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 内部 s2s API,只服务 Zens 子站(如 zdc-shop)。
 * 鉴权由 InternalServiceFilter 统一完成,本控制器不再校验。
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/user")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserPointsService userPointsService;
    private final NotificationService notificationService;

    /**
     * GET /api/internal/user/{userId}/points
     * 返回该用户的当前积分余额。
     */
    @GetMapping("/{userId}/points")
    public Result<?> getPoints(@PathVariable @NotBlank String userId) {
        return Result.success(userPointsService.getPoints(userId));
    }

    /**
     * POST /api/internal/user/{userId}/points/consume
     *
     * 原子扣减积分,失败回 INSUFFICIENT_POINTS。
     * 幂等性: 相同 idempotencyKey 重复请求返回上次结果(DB 唯一索引兜底)。
     */
    @PostMapping("/{userId}/points/consume")
    public Result<?> consumePoints(@PathVariable @NotBlank String userId,
                                   @RequestBody @Valid ConsumeRequest req,
                                   @RequestAttribute(name = "internal.serviceId", required = false) String serviceId) {
        return Result.success(userPointsService.consume(
                userId, req.amount, req.reason, req.orderId, req.idempotencyKey, serviceId));
    }

    /**
     * POST /api/internal/user/{userId}/points/credit
     *
     * 返还积分 (退款 / 补偿 / 奖励发放)。
     * 幂等性: 相同 idempotencyKey 重复请求返回上次结果(DB 唯一索引兜底)。
     */
    @PostMapping("/{userId}/points/credit")
    public Result<?> creditPoints(@PathVariable @NotBlank String userId,
                                  @RequestBody @Valid ConsumeRequest req,
                                  @RequestAttribute(name = "internal.serviceId", required = false) String serviceId) {
        return Result.success(userPointsService.credit(
                userId, req.amount, req.reason, req.orderId, req.idempotencyKey, serviceId));
    }

    /**
     * POST /api/internal/user/{userId}/notifications
     *
     * 子站事件回流主站通知中心。用于积分商城订单、CDK 领取、抽奖开奖等事件。
     */
    @PostMapping("/{userId}/notifications")
    public Result<?> createSubsiteNotification(@PathVariable @NotBlank String userId,
                                               @RequestBody @Valid SubsiteNotificationRequest req) {
        notificationService.createNotification(
                userId,
                NotificationType.SYSTEM,
                req.title,
                req.content,
                req.relatedId,
                null
        );
        return Result.success();
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

    public static class SubsiteNotificationRequest {
        @NotBlank @Size(max = 40)
        @Pattern(regexp = "^[a-z0-9._:-]+$", message = "source 只能包含小写字母、数字、点、下划线、冒号或短横线")
        public String source;

        @NotBlank @Size(max = 120)
        public String title;

        @NotBlank @Size(max = 500)
        public String content;

        @Size(max = 200)
        public String relatedId;
    }
}
