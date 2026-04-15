package com.campus.trend.campus_pulse.utils;

import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * 统一处理 TODAY / WEEK / MONTH 时间范围，严格按自然日、自然周、自然月计算。
 */
public final class TimeRangeUtils {

    private TimeRangeUtils() {
    }

    public static LocalDateTime resolveRangeStart(String timeRange) {
        if (!StringUtils.hasText(timeRange)) {
            return null;
        }

        LocalDate today = LocalDate.now();
        return switch (timeRange.trim().toUpperCase()) {
            case "TODAY" -> today.atStartOfDay();
            case "WEEK" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            case "MONTH" -> today.withDayOfMonth(1).atStartOfDay();
            default -> null;
        };
    }
}
