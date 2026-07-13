package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.trend.campus_pulse.config.properties.PaymentProperties;
import com.campus.trend.campus_pulse.entity.PaymentOrder;
import com.campus.trend.campus_pulse.entity.SupporterEntitlement;
import com.campus.trend.campus_pulse.entity.SupporterPlan;
import com.campus.trend.campus_pulse.mapper.PaymentCallbackEventMapper;
import com.campus.trend.campus_pulse.mapper.PaymentOrderMapper;
import com.campus.trend.campus_pulse.mapper.SupporterEntitlementMapper;
import com.campus.trend.campus_pulse.mapper.SupporterPlanMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.payment.PaymentNotification;
import com.campus.trend.campus_pulse.payment.PaymentProvider;
import com.campus.trend.campus_pulse.payment.PaymentProviderRegistry;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.SupporterVoucherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupporterPaymentServiceImplTest {
    @Mock private SupporterPlanMapper planMapper;
    @Mock private PaymentOrderMapper orderMapper;
    @Mock private PaymentCallbackEventMapper callbackEventMapper;
    @Mock private SupporterEntitlementMapper entitlementMapper;
    @Mock private UserMapper userMapper;
    @Mock private PaymentProvider provider;
    @Mock private Environment environment;
    @Mock private NotificationService notificationService;
    @Mock private SupporterVoucherService voucherService;

    private SupporterPaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        if (TableInfoHelper.getTableInfo(PaymentOrder.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "payment-test"),
                    PaymentOrder.class);
        }
        if (TableInfoHelper.getTableInfo(SupporterEntitlement.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "payment-entitlement-test"),
                    SupporterEntitlement.class);
        }
        when(provider.code()).thenReturn("alipay");
        PaymentProperties properties = new PaymentProperties(environment);
        service = new SupporterPaymentServiceImpl(planMapper, orderMapper, callbackEventMapper,
                entitlementMapper, userMapper, new PaymentProviderRegistry(List.of(provider)),
                properties, new ObjectMapper(), notificationService, voucherService);
    }

    @Test
    void handleCallback_shouldAcceptLateDeliveryWhenGmtPaymentWasBeforeExpiryAndUseDurationSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        PaymentOrder order = order(now.minusMinutes(40), now.minusMinutes(10), 45);
        order.setStatus("EXPIRED");
        PaymentNotification notification = new PaymentNotification("evt-1", order.getOrderNo(),
                "trade-1", "PAID", 900, "CNY", now.minusMinutes(20));
        when(provider.verifyCallback("payload", Map.of())).thenReturn(notification);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(userMapper.lockByIdForUpdate("user-1")).thenReturn("user-1");
        when(entitlementMapper.selectOne(any())).thenReturn(null);

        service.handleCallback("alipay", "payload", Map.of());

        ArgumentCaptor<SupporterEntitlement> entitlement = ArgumentCaptor.forClass(SupporterEntitlement.class);
        verify(entitlementMapper).insert(entitlement.capture());
        verify(voucherService).createGrantAndTryIssue(order, notification.paidAt());
        assertThat(entitlement.getValue().getStartsAt()).isEqualTo(notification.paidAt());
        assertThat(entitlement.getValue().getExpiresAt()).isEqualTo(notification.paidAt().plusDays(45));
        assertThat(order.getStatus()).isEqualTo("PAID");
    }

    @Test
    void handleCallback_shouldThrowForRetryWhenCompareAndSetLosesToNonPaidState() {
        LocalDateTime now = LocalDateTime.now();
        PaymentOrder order = order(now.minusMinutes(5), now.plusMinutes(25), 30);
        when(provider.verifyCallback("payload", Map.of())).thenReturn(new PaymentNotification(
                "evt-retry", order.getOrderNo(), "trade-1", "PAID", 900, "CNY", now));
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(0);
        when(orderMapper.selectById(1L)).thenReturn(PaymentOrder.builder().id(1L).status("EXPIRED").build());

        assertThatThrownBy(() -> service.handleCallback("alipay", "payload", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重试通知");
        verify(entitlementMapper, never()).insert(any(SupporterEntitlement.class));
    }

    @Test
    void handleCallback_shouldRejectNotificationWhoseProviderStatusIsNotPaid() {
        LocalDateTime now = LocalDateTime.now();
        PaymentOrder order = order(now.minusMinutes(5), now.plusMinutes(25), 30);
        when(provider.verifyCallback("payload", Map.of())).thenReturn(new PaymentNotification(
                "evt-2", order.getOrderNo(), "trade-1", "CLOSED", 900, "CNY", now));
        when(orderMapper.selectOne(any())).thenReturn(order);

        service.handleCallback("alipay", "payload", Map.of());

        verify(orderMapper, never()).update(any(), any());
        verify(entitlementMapper, never()).insert(any(SupporterEntitlement.class));
    }

    @Test
    void getCurrentStatus_shouldExposeDeliverablePlusCapabilitiesAndBenefits() {
        LocalDateTime now = LocalDateTime.now();
        when(entitlementMapper.selectOne(any())).thenReturn(SupporterEntitlement.builder()
                .userId("user-1").planCode("supporter_plus_30").planNameSnapshot("Zens 共建支持者")
                .startsAt(now.minusDays(1)).expiresAt(now.plusDays(29)).status("ACTIVE").build());
        when(planMapper.selectOne(any())).thenReturn(SupporterPlan.builder()
                .code("supporter_plus_30").benefitsJson("[\"共建反馈专属通道\"]").build());

        var status = service.getCurrentStatus("user-1");

        assertThat(status.active()).isTrue();
        assertThat(status.remainingDays()).isBetween(28L, 29L);
        assertThat(status.benefits()).containsExactly("共建反馈专属通道");
        assertThat(status.capabilities()).contains("SUPPORTER_BADGE", "PROFILE_CO_BUILDER_BADGE",
                "PRODUCT_FEEDBACK_CHANNEL");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SupporterEntitlement>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(entitlementMapper).selectOne(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment())
                .contains("starts_at", "<=", "expires_at", ">");
    }

    @Test
    void getCurrentStatus_shouldReturnEmptyFactsWhenEntitlementIsMissing() {
        when(entitlementMapper.selectOne(any())).thenReturn(null);

        var status = service.getCurrentStatus("user-1");

        assertThat(status.active()).isFalse();
        assertThat(status.remainingDays()).isZero();
        assertThat(status.benefits()).isEmpty();
        assertThat(status.capabilities()).isEmpty();
    }

    private PaymentOrder order(LocalDateTime createdAt, LocalDateTime expiresAt, int durationDays) {
        return PaymentOrder.builder()
                .id(1L).orderNo("ZS-1").userId("user-1").planCode("supporter")
                .planNameSnapshot("支持者").durationDaysSnapshot(durationDays).amountCents(900)
                .currency("CNY").provider("alipay").providerOrderNo("trade-1")
                .status("PENDING").createdAt(createdAt).expiresAt(expiresAt).updatedAt(createdAt)
                .build();
    }
}
