package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.common.notification.NotificationType;
import com.campus.trend.campus_pulse.config.properties.PaymentProperties;
import com.campus.trend.campus_pulse.dto.request.SupporterOrderCreateReq;
import com.campus.trend.campus_pulse.dto.response.PaymentOrderResp;
import com.campus.trend.campus_pulse.dto.response.SupporterPlanResp;
import com.campus.trend.campus_pulse.dto.response.SupporterStatusResp;
import com.campus.trend.campus_pulse.entity.PaymentCallbackEvent;
import com.campus.trend.campus_pulse.entity.PaymentOrder;
import com.campus.trend.campus_pulse.entity.SupporterEntitlement;
import com.campus.trend.campus_pulse.entity.SupporterPlan;
import com.campus.trend.campus_pulse.mapper.PaymentCallbackEventMapper;
import com.campus.trend.campus_pulse.mapper.PaymentOrderMapper;
import com.campus.trend.campus_pulse.mapper.SupporterEntitlementMapper;
import com.campus.trend.campus_pulse.mapper.SupporterPlanMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.payment.PaymentCheckout;
import com.campus.trend.campus_pulse.payment.PaymentNotification;
import com.campus.trend.campus_pulse.payment.PaymentProvider;
import com.campus.trend.campus_pulse.payment.PaymentProviderRegistry;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.SupporterPaymentService;
import com.campus.trend.campus_pulse.service.SupporterVoucherService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupporterPaymentServiceImpl implements SupporterPaymentService {
    private static final String PENDING = "PENDING";
    private static final String PAID = "PAID";

    private final SupporterPlanMapper planMapper;
    private final PaymentOrderMapper orderMapper;
    private final PaymentCallbackEventMapper callbackEventMapper;
    private final SupporterEntitlementMapper entitlementMapper;
    private final UserMapper userMapper;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final SupporterVoucherService voucherService;

    @Override
    public List<SupporterPlanResp> listPlans() {
        return planMapper.selectList(new LambdaQueryWrapper<SupporterPlan>()
                        .eq(SupporterPlan::getActive, true)
                        .orderByDesc(SupporterPlan::getSortWeight)
                        .orderByAsc(SupporterPlan::getPriceCents))
                .stream().map(this::toPlanResp).toList();
    }

    @Override
    public PaymentOrderResp createOrder(String userId, SupporterOrderCreateReq request) {
        if (!paymentProperties.isEnabled()) {
            throw new BusinessException(ResultCode.FAILED, "在线支付尚未开放，方案已准备好但不会产生真实扣款");
        }
        PaymentOrder existing = findByIdempotency(userId, request.idempotencyKey());
        if (existing != null) {
            expireIfNeeded(existing);
            return toOrderResp(existing);
        }

        SupporterPlan plan = planMapper.selectOne(new LambdaQueryWrapper<SupporterPlan>()
                .eq(SupporterPlan::getCode, request.planCode().trim())
                .eq(SupporterPlan::getActive, true)
                .last("LIMIT 1"));
        if (plan == null) throw new BusinessException(ResultCode.FAILED, "支持者方案不存在或已下架");
        if (plan.getPriceCents() == null || plan.getPriceCents() <= 0) {
            throw new BusinessException(ResultCode.FAILED, "支持者方案价格配置无效");
        }
        if (plan.getDurationDays() == null || plan.getDurationDays() <= 0) {
            throw new BusinessException(ResultCode.FAILED, "支持者方案权益期限配置无效");
        }

        String providerCode = paymentProperties.getProvider().toLowerCase(Locale.ROOT);
        PaymentProvider provider = providerRegistry.require(providerCode);
        LocalDateTime now = LocalDateTime.now();
        PaymentOrder order = PaymentOrder.builder()
                .orderNo(newOrderNo())
                .userId(userId)
                .planCode(plan.getCode())
                .planNameSnapshot(plan.getName())
                .durationDaysSnapshot(plan.getDurationDays())
                .amountCents(plan.getPriceCents())
                .currency(plan.getCurrency())
                .provider(providerCode)
                .status(PENDING)
                .idempotencyKey(request.idempotencyKey())
                .expiresAt(now.plusMinutes(paymentProperties.getOrderExpireMinutes()))
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            PaymentOrder duplicate = findByIdempotency(userId, request.idempotencyKey());
            if (duplicate != null) return toOrderResp(duplicate);
            throw e;
        }

        try {
            // 外部支付服务商调用不能占用数据库事务；订单先落账，再用同一 orderNo 作为渠道幂等键。
            PaymentCheckout checkout = provider.createCheckout(order);
            if (checkout.checkoutUrl() == null || checkout.checkoutUrl().isBlank()) {
                throw new IllegalStateException("支付服务商未返回有效收银台地址");
            }
            order.setProviderOrderNo(checkout.providerOrderNo());
            order.setCheckoutUrl(checkout.checkoutUrl());
            order.setUpdatedAt(LocalDateTime.now());
            if (orderMapper.updateById(order) != 1) {
                throw new IllegalStateException("支付订单收银台信息保存失败");
            }
        } catch (RuntimeException e) {
            order.setStatus("FAILED");
            order.setFailureReason(safeFailureReason(e.getMessage()));
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.updateById(order);
            throw e;
        }
        log.info("[payment] order created orderNo={}, userId={}, plan={}, provider={}",
                order.getOrderNo(), userId, plan.getCode(), providerCode);
        return toOrderResp(order);
    }

    @Override
    public PaymentOrderResp getOrder(String userId, String orderNo) {
        PaymentOrder order = findOwnedOrder(userId, orderNo);
        expireIfNeeded(order);
        return toOrderResp(order);
    }

    @Override
    public SupporterStatusResp getCurrentStatus(String userId) {
        LocalDateTime now = LocalDateTime.now();
        SupporterEntitlement entitlement = entitlementMapper.selectOne(
                new LambdaQueryWrapper<SupporterEntitlement>()
                        .eq(SupporterEntitlement::getUserId, userId)
                        .eq(SupporterEntitlement::getStatus, "ACTIVE")
                        .le(SupporterEntitlement::getStartsAt, now)
                        .gt(SupporterEntitlement::getExpiresAt, now)
                        .orderByDesc(SupporterEntitlement::getExpiresAt)
                        .last("LIMIT 1"));
        if (entitlement == null) {
            return new SupporterStatusResp(false, null, null, null, null, 0, List.of(), List.of());
        }
        SupporterPlan plan = planMapper.selectOne(new LambdaQueryWrapper<SupporterPlan>()
                .eq(SupporterPlan::getCode, entitlement.getPlanCode()).last("LIMIT 1"));
        List<String> benefits = plan == null ? List.of() : parseBenefits(plan);
        List<String> capabilities = capabilitiesFor(entitlement.getPlanCode());
        long remainingDays = Math.max(1, (Duration.between(now, entitlement.getExpiresAt()).toHours() + 23) / 24);
        return new SupporterStatusResp(true, entitlement.getPlanCode(), entitlement.getPlanNameSnapshot(),
                entitlement.getStartsAt(), entitlement.getExpiresAt(), remainingDays, benefits, capabilities);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(String providerCode, String rawBody, Map<String, String> headers) {
        if (rawBody == null || rawBody.isBlank() || rawBody.length() > 65_536) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "支付回调内容为空或过大");
        }
        PaymentProvider provider = providerRegistry.require(providerCode);
        PaymentNotification notification = provider.verifyCallback(rawBody, headers);

        PaymentCallbackEvent previous = callbackEventMapper.selectOne(
                new LambdaQueryWrapper<PaymentCallbackEvent>()
                        .eq(PaymentCallbackEvent::getProvider, provider.code())
                        .eq(PaymentCallbackEvent::getEventId, notification.eventId())
                        .last("LIMIT 1"));
        if (previous != null) return;

        PaymentCallbackEvent event = PaymentCallbackEvent.builder()
                .provider(provider.code())
                .eventId(notification.eventId())
                .orderNo(notification.orderNo())
                .payloadHash(sha256(rawBody))
                .signatureValid(true)
                .status("RECEIVED")
                .receivedAt(LocalDateTime.now())
                .build();
        try {
            callbackEventMapper.insert(event);
        } catch (DuplicateKeyException duplicate) {
            return;
        }

        PaymentOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, notification.orderNo()).last("LIMIT 1"));
        if (order == null) {
            rejectEvent(event, "订单不存在");
            return;
        }
        if (!order.getProvider().equals(provider.code())) {
            rejectEvent(event, "支付渠道与订单不一致");
            return;
        }
        if (order.getProviderOrderNo() != null
                && !order.getProviderOrderNo().equals(notification.providerOrderNo())) {
            rejectEvent(event, "服务商订单号与订单不一致");
            return;
        }
        if (order.getAmountCents() != notification.amountCents()
                || !order.getCurrency().equalsIgnoreCase(notification.currency())) {
            rejectEvent(event, "回调金额或币种与订单不一致");
            return;
        }
        if (!PAID.equals(notification.status())) {
            rejectEvent(event, "支付通知状态不是 PAID");
            return;
        }
        if (PAID.equals(order.getStatus())) {
            finishEvent(event, "DUPLICATE");
            return;
        }
        if (!PENDING.equals(order.getStatus()) && !"EXPIRED".equals(order.getStatus())) {
            rejectEvent(event, "订单状态不允许支付");
            return;
        }
        LocalDateTime paidAt = notification.paidAt();
        if (paidAt == null) {
            rejectEvent(event, "支付通知缺少支付时间");
            return;
        }
        if (paidAt.isAfter(LocalDateTime.now().plusMinutes(10))
                || paidAt.isBefore(order.getCreatedAt().minusMinutes(10))
                || paidAt.isAfter(order.getExpiresAt())) {
            rejectEvent(event, "支付时间超出订单有效范围");
            return;
        }
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, order.getId())
                .in(PaymentOrder::getStatus, PENDING, "EXPIRED")
                .set(PaymentOrder::getStatus, PAID)
                .set(PaymentOrder::getPaidAt, paidAt)
                .set(PaymentOrder::getProviderOrderNo, notification.providerOrderNo())
                .set(PaymentOrder::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            PaymentOrder latest = orderMapper.selectById(order.getId());
            if (latest == null || !PAID.equals(latest.getStatus())) {
                throw new IllegalStateException("支付订单并发状态冲突，请渠道重试通知");
            }
            finishEvent(event, "DUPLICATE");
            return;
        }

        order.setStatus(PAID);
        order.setPaidAt(paidAt);
        order.setProviderOrderNo(notification.providerOrderNo());
        grantEntitlement(order, paidAt);
        // 公益站兑换码来自管理员预生成库存；缺货时只保留 PENDING 发放记录，不影响支付权益生效。
        voucherService.createGrantAndTryIssue(order, paidAt);
        finishEvent(event, "PROCESSED");
        notificationService.createNotification(order.getUserId(), NotificationType.SYSTEM,
                "Zens 支持者权益已生效",
                "感谢你的支持，「" + order.getPlanNameSnapshot() + "」已生效，可在支持者页面查看期限。",
                order.getOrderNo(), null);
        log.info("[payment] paid orderNo={}, userId={}, providerEvent={}",
                order.getOrderNo(), order.getUserId(), notification.eventId());
    }

    private void grantEntitlement(PaymentOrder order, LocalDateTime paidAt) {
        if (!PAID.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.FAILED, "仅已支付订单可以发放支持者权益");
        }
        if (userMapper.lockByIdForUpdate(order.getUserId()) == null) {
            throw new BusinessException(ResultCode.FAILED, "支付订单对应用户不存在");
        }
        SupporterEntitlement existing = entitlementMapper.selectOne(
                new LambdaQueryWrapper<SupporterEntitlement>()
                        .eq(SupporterEntitlement::getSourceOrderNo, order.getOrderNo()).last("LIMIT 1"));
        if (existing != null) return;
        Integer durationDays = order.getDurationDaysSnapshot();
        if (durationDays == null || durationDays <= 0) {
            throw new BusinessException(ResultCode.FAILED, "订单权益期限快照无效");
        }
        SupporterEntitlement latest = entitlementMapper.selectOne(
                new LambdaQueryWrapper<SupporterEntitlement>()
                        .eq(SupporterEntitlement::getUserId, order.getUserId())
                        .eq(SupporterEntitlement::getStatus, "ACTIVE")
                        .orderByDesc(SupporterEntitlement::getExpiresAt).last("LIMIT 1"));
        LocalDateTime startsAt = paidAt;
        if (latest != null && latest.getExpiresAt().isAfter(startsAt)) startsAt = latest.getExpiresAt();
        entitlementMapper.insert(SupporterEntitlement.builder()
                .userId(order.getUserId())
                .planCode(order.getPlanCode())
                .planNameSnapshot(order.getPlanNameSnapshot())
                .sourceOrderNo(order.getOrderNo())
                .startsAt(startsAt)
                .expiresAt(startsAt.plusDays(durationDays))
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void finishEvent(PaymentCallbackEvent event, String status) {
        event.setStatus(status);
        event.setProcessedAt(LocalDateTime.now());
        callbackEventMapper.updateById(event);
    }

    private void rejectEvent(PaymentCallbackEvent event, String message) {
        event.setStatus("REJECTED");
        event.setErrorMessage(message);
        event.setProcessedAt(LocalDateTime.now());
        callbackEventMapper.updateById(event);
        log.warn("[payment] callback rejected provider={}, eventId={}, reason={}",
                event.getProvider(), event.getEventId(), message);
    }

    private PaymentOrder findByIdempotency(String userId, String key) {
        return orderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getUserId, userId)
                .eq(PaymentOrder::getIdempotencyKey, key).last("LIMIT 1"));
    }

    private PaymentOrder findOwnedOrder(String userId, String orderNo) {
        PaymentOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getUserId, userId)
                .eq(PaymentOrder::getOrderNo, orderNo).last("LIMIT 1"));
        if (order == null) throw new BusinessException(ResultCode.FAILED, "支付订单不存在");
        return order;
    }

    private void expireIfNeeded(PaymentOrder order) {
        if (!PENDING.equals(order.getStatus()) || !LocalDateTime.now().isAfter(order.getExpiresAt())) return;
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, order.getId())
                .eq(PaymentOrder::getStatus, PENDING)
                .set(PaymentOrder::getStatus, "EXPIRED")
                .set(PaymentOrder::getUpdatedAt, LocalDateTime.now()));
        if (updated == 1) order.setStatus("EXPIRED");
    }

    private String safeFailureReason(String message) {
        if (message == null || message.isBlank()) return "PAYMENT_PROVIDER_ERROR";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private SupporterPlanResp toPlanResp(SupporterPlan plan) {
        try {
            List<String> benefits = parseBenefits(plan);
            return new SupporterPlanResp(plan.getCode(), plan.getName(), plan.getDescription(), plan.getPriceCents(),
                    plan.getCurrency(), plan.getDurationDays(), benefits,
                    paymentProperties.isEnabled() && !"disabled".equals(paymentProperties.getProvider()));
        } catch (Exception e) {
            throw new IllegalStateException("支持者方案 benefits_json 配置无效: " + plan.getCode(), e);
        }
    }

    private List<String> parseBenefits(SupporterPlan plan) {
        try {
            if (plan.getBenefitsJson() == null || plan.getBenefitsJson().isBlank()) return List.of();
            return objectMapper.readValue(plan.getBenefitsJson(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("支持者方案 benefits_json 配置无效: " + plan.getCode(), e);
        }
    }

    /** 能力标识只描述可交付的界面权益，不参与内容推荐、审核或发帖权限。 */
    private List<String> capabilitiesFor(String planCode) {
        if ("supporter_plus_30".equals(planCode)) {
            return List.of("SUPPORTER_BADGE", "PROFILE_SUPPORTER_ACCENT", "PROFILE_CO_BUILDER_BADGE", "PRODUCT_FEEDBACK_CHANNEL");
        }
        return List.of("SUPPORTER_BADGE", "PROFILE_SUPPORTER_ACCENT");
    }

    private PaymentOrderResp toOrderResp(PaymentOrder order) {
        return new PaymentOrderResp(order.getOrderNo(), order.getPlanCode(), order.getPlanNameSnapshot(),
                order.getAmountCents(), order.getCurrency(), order.getProvider(), order.getStatus(),
                order.getCheckoutUrl(), order.getPaidAt(), order.getExpiresAt(), order.getCreatedAt());
    }

    private String newOrderNo() {
        return "ZS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算支付回调摘要", e);
        }
    }
}
