package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.SupporterCreatorInsightDailyResp;
import com.campus.trend.campus_pulse.dto.response.SupporterCreatorInsightSummaryResp;
import com.campus.trend.campus_pulse.mapper.SupporterCreatorInsightsMapper;
import com.campus.trend.campus_pulse.mapper.SupporterEntitlementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupporterCreatorInsightsServiceImplTest {
    @Mock private SupporterEntitlementMapper entitlementMapper;
    @Mock private SupporterCreatorInsightsMapper insightsMapper;

    private SupporterCreatorInsightsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SupporterCreatorInsightsServiceImpl(entitlementMapper, insightsMapper);
    }

    @Test
    void getInsights_shouldRejectInvalidDayRangeBeforeQueryingEntitlement() {
        assertThatThrownBy(() -> service.getInsights("user-1", 6))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
        assertThatThrownBy(() -> service.getInsights("user-1", 91))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
        verify(entitlementMapper, never()).selectCount(any());
    }

    @Test
    void getInsights_shouldRejectUserWithoutActiveEntitlement() {
        when(entitlementMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.getInsights("user-1", 30))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getResultCode()).isEqualTo(ResultCode.NO_PERMISSION));
        verify(insightsMapper, never()).selectSummary(any());
        verify(insightsMapper, never()).selectDailyTrend(any(), any(), any());
    }

    @Test
    void getInsights_shouldReturnContinuousTrendAndPreserveAggregates() {
        when(entitlementMapper.selectCount(any())).thenReturn(1L);
        when(insightsMapper.selectSummary("user-1")).thenReturn(
                new SupporterCreatorInsightSummaryResp(3, 120, 18, 7, 11, 42));
        LocalDate today = LocalDate.now();
        when(insightsMapper.selectDailyTrend(eq("user-1"), any(), any())).thenReturn(List.of(
                new SupporterCreatorInsightDailyResp(today.minusDays(2), 9, 2, 1, 3, 36),
                new SupporterCreatorInsightDailyResp(today, 5, 1, 0, 1, 28)));

        var result = service.getInsights("user-1", 7);

        assertThat(result.days()).isEqualTo(7);
        assertThat(result.fromDate()).isEqualTo(today.minusDays(6));
        assertThat(result.toDate()).isEqualTo(today);
        assertThat(result.summary().getPublishedPosts()).isEqualTo(3);
        assertThat(result.trend()).hasSize(7);
        assertThat(result.trend()).extracting(SupporterCreatorInsightDailyResp::getDate)
                .containsExactly(today.minusDays(6), today.minusDays(5), today.minusDays(4),
                        today.minusDays(3), today.minusDays(2), today.minusDays(1), today);
        assertThat(result.trend().get(4).getViews()).isEqualTo(9);
        assertThat(result.trend().get(5).getViews()).isZero();
    }

    @Test
    void getInsights_shouldReturnZeroSummaryAndZeroTrendWhenThereIsNoContent() {
        when(entitlementMapper.selectCount(any())).thenReturn(1L);
        when(insightsMapper.selectSummary("user-1")).thenReturn(null);
        when(insightsMapper.selectDailyTrend(eq("user-1"), any(), any())).thenReturn(List.of());

        var result = service.getInsights("user-1", 30);

        assertThat(result.summary().getPublishedPosts()).isZero();
        assertThat(result.summary().getTotalViews()).isZero();
        assertThat(result.trend()).hasSize(30).allSatisfy(day -> {
            assertThat(day.getViews()).isZero();
            assertThat(day.getLikes()).isZero();
            assertThat(day.getCollects()).isZero();
            assertThat(day.getComments()).isZero();
            assertThat(day.getAvgDwellSec()).isZero();
        });
    }
}
