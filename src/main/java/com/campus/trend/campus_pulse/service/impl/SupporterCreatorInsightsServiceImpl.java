package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.SupporterCreatorInsightDailyResp;
import com.campus.trend.campus_pulse.dto.response.SupporterCreatorInsightSummaryResp;
import com.campus.trend.campus_pulse.dto.response.SupporterCreatorInsightsResp;
import com.campus.trend.campus_pulse.entity.SupporterEntitlement;
import com.campus.trend.campus_pulse.mapper.SupporterCreatorInsightsMapper;
import com.campus.trend.campus_pulse.mapper.SupporterEntitlementMapper;
import com.campus.trend.campus_pulse.service.SupporterCreatorInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupporterCreatorInsightsServiceImpl implements SupporterCreatorInsightsService {
    static final int MIN_DAYS = 7;
    static final int MAX_DAYS = 90;

    private final SupporterEntitlementMapper entitlementMapper;
    private final SupporterCreatorInsightsMapper insightsMapper;

    @Override
    public SupporterCreatorInsightsResp getInsights(String userId, int days) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.TOKEN_MISSING, "请先登录后查看创作简报");
        }
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "统计周期必须在 " + MIN_DAYS + " 至 " + MAX_DAYS + " 天之间");
        }

        LocalDateTime now = LocalDateTime.now();
        requireActiveEntitlement(userId, now);

        LocalDate toDate = now.toLocalDate();
        LocalDate fromDate = toDate.minusDays(days - 1L);
        SupporterCreatorInsightSummaryResp summary = insightsMapper.selectSummary(userId);
        if (summary == null) {
            summary = new SupporterCreatorInsightSummaryResp();
        }
        List<SupporterCreatorInsightDailyResp> rows = insightsMapper.selectDailyTrend(
                userId, fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay());

        return new SupporterCreatorInsightsResp(days, fromDate, toDate, now, summary,
                fillMissingDates(fromDate, toDate, rows));
    }

    private void requireActiveEntitlement(String userId, LocalDateTime now) {
        Long count = entitlementMapper.selectCount(new LambdaQueryWrapper<SupporterEntitlement>()
                .eq(SupporterEntitlement::getUserId, userId)
                .eq(SupporterEntitlement::getStatus, "ACTIVE")
                .le(SupporterEntitlement::getStartsAt, now)
                .gt(SupporterEntitlement::getExpiresAt, now));
        if (count == null || count <= 0) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该创作简报仅向有效 Zens 支持者开放");
        }
    }

    private List<SupporterCreatorInsightDailyResp> fillMissingDates(
            LocalDate fromDate, LocalDate toDate, List<SupporterCreatorInsightDailyResp> rows) {
        Map<LocalDate, SupporterCreatorInsightDailyResp> byDate = new LinkedHashMap<>();
        if (rows != null) {
            rows.stream()
                    .filter(row -> row != null && row.getDate() != null)
                    .filter(row -> !row.getDate().isBefore(fromDate) && !row.getDate().isAfter(toDate))
                    .forEach(row -> byDate.put(row.getDate(), row));
        }
        return fromDate.datesUntil(toDate.plusDays(1))
                .map(date -> byDate.getOrDefault(date,
                        new SupporterCreatorInsightDailyResp(date, 0, 0, 0, 0, 0)))
                .toList();
    }
}
