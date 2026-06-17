package com.campus.trend.campus_pulse.monitor;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

@Component
public class PerformanceEventRecorder {

    private static final int MAX_EVENTS = 200;

    private final Deque<SlowRequestEvent> slowRequests = new ArrayDeque<>();
    private final Deque<SlowSqlEvent> slowSqlEvents = new ArrayDeque<>();

    public void recordSlowRequest(String method, String uri, int status, long costMs) {
        synchronized (slowRequests) {
            slowRequests.addFirst(new SlowRequestEvent(LocalDateTime.now(), method, uri, status, costMs));
            trim(slowRequests);
        }
    }

    public void recordSlowSql(String mapperId, long costMs, String sql) {
        synchronized (slowSqlEvents) {
            slowSqlEvents.addFirst(new SlowSqlEvent(LocalDateTime.now(), mapperId, costMs, sql));
            trim(slowSqlEvents);
        }
    }

    public List<SlowRequestEvent> listSlowRequests(String keyword, Long minMs, Integer limit) {
        List<SlowRequestEvent> snapshot;
        synchronized (slowRequests) {
            snapshot = new ArrayList<>(slowRequests);
        }
        return snapshot.stream()
                .filter(item -> item.costMs() >= normalizeMinMs(minMs))
                .filter(item -> matches(keyword, item.method(), item.uri(), String.valueOf(item.status())))
                .sorted(Comparator.comparing(SlowRequestEvent::time).reversed())
                .limit(normalizeLimit(limit))
                .toList();
    }

    public List<SlowSqlEvent> listSlowSql(String keyword, Long minMs, Integer limit) {
        List<SlowSqlEvent> snapshot;
        synchronized (slowSqlEvents) {
            snapshot = new ArrayList<>(slowSqlEvents);
        }
        return snapshot.stream()
                .filter(item -> item.costMs() >= normalizeMinMs(minMs))
                .filter(item -> matches(keyword, item.mapperId(), item.sql()))
                .sorted(Comparator.comparing(SlowSqlEvent::time).reversed())
                .limit(normalizeLimit(limit))
                .toList();
    }

    private static <T> void trim(Deque<T> queue) {
        while (queue.size() > MAX_EVENTS) {
            queue.removeLast();
        }
    }

    private static long normalizeMinMs(Long minMs) {
        return minMs == null ? 0L : Math.max(0L, minMs);
    }

    private static int normalizeLimit(Integer limit) {
        return Math.min(Math.max(limit == null ? 50 : limit, 1), MAX_EVENTS);
    }

    private static boolean matches(String keyword, String... values) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public record SlowRequestEvent(LocalDateTime time, String method, String uri, int status, long costMs) {
    }

    public record SlowSqlEvent(LocalDateTime time, String mapperId, long costMs, String sql) {
    }
}
