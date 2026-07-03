package com.campus.trend.campus_pulse.monitor;

import com.campus.trend.campus_pulse.dto.request.WebVitalMetricReq;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class WebVitalRecorder {

    private static final int MAX_EVENTS = 300;
    private static final List<String> KNOWN_METRICS = List.of("LCP", "CLS", "INP", "FCP", "TTFB");

    private final Deque<WebVitalEvent> events = new ArrayDeque<>();

    public void record(WebVitalMetricReq request) {
        if (request == null || !StringUtils.hasText(request.getName()) || request.getValue() == null) {
            return;
        }

        String name = request.getName().trim().toUpperCase(Locale.ROOT);
        if (!KNOWN_METRICS.contains(name)) {
            return;
        }

        double value = Math.max(0D, request.getValue());
        String route = normalizeRoute(request.getRoute());
        String rating = StringUtils.hasText(request.getRating()) ? request.getRating().trim().toLowerCase(Locale.ROOT) : "unknown";
        LocalDateTime time = request.getTimestamp() != null && request.getTimestamp() > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(request.getTimestamp()), ZoneId.systemDefault())
                : LocalDateTime.now();

        synchronized (events) {
            events.addFirst(new WebVitalEvent(
                    time,
                    name,
                    value,
                    rating,
                    route,
                    abbreviate(request.getNavigationType(), 24),
                    abbreviate(request.getUserAgent(), 180)));
            while (events.size() > MAX_EVENTS) {
                events.removeLast();
            }
        }
    }

    public List<WebVitalEvent> list(String metric, String route, Integer limit) {
        List<WebVitalEvent> snapshot;
        synchronized (events) {
            snapshot = new ArrayList<>(events);
        }
        String normalizedMetric = StringUtils.hasText(metric) ? metric.trim().toUpperCase(Locale.ROOT) : "";
        String routeNeedle = StringUtils.hasText(route) ? route.trim().toLowerCase(Locale.ROOT) : "";
        return snapshot.stream()
                .filter(item -> !StringUtils.hasText(normalizedMetric) || item.name().equals(normalizedMetric))
                .filter(item -> !StringUtils.hasText(routeNeedle) || item.route().toLowerCase(Locale.ROOT).contains(routeNeedle))
                .sorted(Comparator.comparing(WebVitalEvent::time).reversed())
                .limit(normalizeLimit(limit))
                .toList();
    }

    public Map<String, Object> summary() {
        List<WebVitalEvent> snapshot;
        synchronized (events) {
            snapshot = new ArrayList<>(events);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", snapshot.size());
        result.put("metrics", KNOWN_METRICS.stream().collect(Collectors.toMap(
                name -> name,
                name -> summarizeMetric(snapshot, name),
                (left, right) -> left,
                LinkedHashMap::new)));
        result.put("recent", snapshot.stream()
                .sorted(Comparator.comparing(WebVitalEvent::time).reversed())
                .limit(10)
                .toList());
        return result;
    }

    private Map<String, Object> summarizeMetric(List<WebVitalEvent> snapshot, String metric) {
        List<Double> values = snapshot.stream()
                .filter(item -> metric.equals(item.name()))
                .map(WebVitalEvent::value)
                .sorted()
                .toList();
        long poorCount = snapshot.stream()
                .filter(item -> metric.equals(item.name()))
                .filter(item -> "poor".equalsIgnoreCase(item.rating()))
                .count();
        if (values.isEmpty()) {
            return Map.of("count", 0, "p75", 0, "avg", 0, "poor", 0);
        }
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        return Map.of(
                "count", values.size(),
                "p75", round(values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * 0.75D) - 1))),
                "avg", round(avg),
                "poor", poorCount);
    }

    private String normalizeRoute(String route) {
        if (!StringUtils.hasText(route)) {
            return "/";
        }
        String normalized = route.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120);
        }
        return normalized;
    }

    private String abbreviate(String value, int maxLen) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() > maxLen ? normalized.substring(0, maxLen) : normalized;
    }

    private static int normalizeLimit(Integer limit) {
        return Math.min(Math.max(limit == null ? 80 : limit, 1), MAX_EVENTS);
    }

    private static double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    public record WebVitalEvent(
            LocalDateTime time,
            String name,
            double value,
            String rating,
            String route,
            String navigationType,
            String userAgent) {
    }
}
