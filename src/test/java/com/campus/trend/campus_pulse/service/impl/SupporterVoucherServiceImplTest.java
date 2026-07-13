package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.config.properties.SupporterVoucherProperties;
import com.campus.trend.campus_pulse.dto.request.SupporterVoucherImportReq;
import com.campus.trend.campus_pulse.entity.PaymentOrder;
import com.campus.trend.campus_pulse.entity.SupporterVoucherCode;
import com.campus.trend.campus_pulse.entity.SupporterVoucherGrant;
import com.campus.trend.campus_pulse.mapper.SupporterVoucherCodeMapper;
import com.campus.trend.campus_pulse.mapper.SupporterVoucherGrantMapper;
import com.campus.trend.campus_pulse.service.SupporterVoucherCrypto;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupporterVoucherServiceImplTest {
    @Mock private SupporterVoucherCodeMapper codeMapper;
    @Mock private SupporterVoucherGrantMapper grantMapper;
    @Mock private SupporterVoucherCrypto crypto;

    private SupporterVoucherServiceImpl service;

    @BeforeEach
    void setUp() {
        initTable(SupporterVoucherCode.class);
        initTable(SupporterVoucherGrant.class);
        SupporterVoucherProperties properties = new SupporterVoucherProperties();
        properties.setRedemptionUrl("https://pip.kdns.fr");
        service = new SupporterVoucherServiceImpl(codeMapper, grantMapper, crypto, properties);
    }

    @Test
    void createGrant_shouldKeepPendingWhenRealInventoryIsEmptyWithoutFailingPayment() {
        when(grantMapper.lockBySourceOrderNo("ORDER-1")).thenReturn(null);
        when(grantMapper.insert(any(SupporterVoucherGrant.class))).thenAnswer(invocation -> {
            invocation.<SupporterVoucherGrant>getArgument(0).setId(11L);
            return 1;
        });
        when(codeMapper.lockOldestAvailable(30)).thenReturn(null);

        service.createGrantAndTryIssue(paidOrder("supporter_30"), LocalDateTime.now());

        ArgumentCaptor<SupporterVoucherGrant> grant = ArgumentCaptor.forClass(SupporterVoucherGrant.class);
        verify(grantMapper).insert(grant.capture());
        assertThat(grant.getValue().getQuota()).isEqualTo(30);
        assertThat(grant.getValue().getStatus()).isEqualTo("PENDING");
        verify(codeMapper, never()).assignIfAvailable(any(), any(), any());
    }

    @Test
    void createGrant_shouldAssignOldestAvailableCodeToPlusGrant() {
        when(grantMapper.lockBySourceOrderNo("ORDER-1")).thenReturn(null);
        when(grantMapper.insert(any(SupporterVoucherGrant.class))).thenAnswer(invocation -> {
            invocation.<SupporterVoucherGrant>getArgument(0).setId(12L);
            return 1;
        });
        when(codeMapper.lockOldestAvailable(50)).thenReturn(SupporterVoucherCode.builder()
                .id(99L).quota(50).status("AVAILABLE").build());
        when(codeMapper.assignIfAvailable(any(), any(), any())).thenReturn(1);
        when(grantMapper.issueIfPending(any(), any(), any())).thenReturn(1);

        service.createGrantAndTryIssue(paidOrder("supporter_plus_30"), LocalDateTime.now());

        verify(codeMapper).assignIfAvailable(org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq(12L), any());
        verify(grantMapper).issueIfPending(org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(99L), any());
    }

    @Test
    void importCodes_shouldDeduplicateEncryptAndBackfillOldestPendingGrant() {
        when(crypto.hash("CODE-A")).thenReturn("hash-a");
        when(crypto.hash("CODE-B")).thenReturn("hash-b");
        when(crypto.encrypt(any())).thenAnswer(invocation -> "encrypted:" + invocation.getArgument(0));
        when(codeMapper.selectCount(any())).thenReturn(0L);
        when(codeMapper.insert(any(SupporterVoucherCode.class))).thenReturn(1);
        SupporterVoucherGrant pending = SupporterVoucherGrant.builder()
                .id(5L).quota(30).status("PENDING").build();
        when(grantMapper.lockOldestPending(30)).thenReturn(pending).thenReturn(null);
        when(codeMapper.lockOldestAvailable(30)).thenReturn(
                SupporterVoucherCode.builder().id(7L).quota(30).status("AVAILABLE").build());
        when(codeMapper.assignIfAvailable(any(), any(), any())).thenReturn(1);
        when(grantMapper.issueIfPending(any(), any(), any())).thenReturn(1);

        try (MockedStatic<PermissionUtils> permission = mockStatic(PermissionUtils.class)) {
            permission.when(PermissionUtils::isAdmin).thenReturn(true);
            var result = service.importCodes("admin-1",
                    new SupporterVoucherImportReq(30, List.of(" CODE-A ", "CODE-A", "CODE-B")));

            assertThat(result.imported()).isEqualTo(2);
            assertThat(result.duplicates()).isEqualTo(1);
            assertThat(result.pendingIssued()).isEqualTo(1);
        }
    }

    @Test
    void inventory_shouldEnforceAdminPermissionInsideServiceLayer() {
        try (MockedStatic<PermissionUtils> permission = mockStatic(PermissionUtils.class)) {
            permission.when(PermissionUtils::isAdmin).thenReturn(false);
            assertThatThrownBy(service::inventory)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅管理员");
        }
        verify(codeMapper, never()).selectCount(any());
    }

    private PaymentOrder paidOrder(String planCode) {
        return PaymentOrder.builder().orderNo("ORDER-1").userId("user-1")
                .planCode(planCode).status("PAID").build();
    }

    private void initTable(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "voucher-test"), type);
        }
    }
}
