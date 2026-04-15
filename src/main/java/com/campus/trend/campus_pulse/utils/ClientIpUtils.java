package com.campus.trend.campus_pulse.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 统一处理反向代理场景下的客户端真实 IP 解析。
 */
public final class ClientIpUtils {

    private static final String UNKNOWN = "unknown";

    private ClientIpUtils() {
    }

    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        collectForwardedHeader(candidates, request.getHeader("Forwarded"));
        collectCommaSeparatedHeader(candidates, request.getHeader("X-Forwarded-For"));
        addCandidate(candidates, request.getHeader("X-Real-IP"));
        addCandidate(candidates, request.getHeader("Proxy-Client-IP"));
        addCandidate(candidates, request.getHeader("WL-Proxy-Client-IP"));
        addCandidate(candidates, request.getHeader("HTTP_CLIENT_IP"));
        addCandidate(candidates, request.getHeader("HTTP_X_FORWARDED_FOR"));
        addCandidate(candidates, request.getRemoteAddr());

        String firstValid = null;
        String firstNonLoopback = null;
        for (String candidate : candidates) {
            String normalized = normalizeIp(candidate);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (firstValid == null) {
                firstValid = normalized;
            }
            if (!isLoopback(normalized) && firstNonLoopback == null) {
                firstNonLoopback = normalized;
            }
            if (isPublicIp(normalized)) {
                return normalized;
            }
        }

        if (firstNonLoopback != null) {
            return firstNonLoopback;
        }
        return firstValid;
    }

    private static void collectForwardedHeader(List<String> candidates, String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return;
        }
        String[] segments = headerValue.split(",");
        for (String segment : segments) {
            String[] directives = segment.split(";");
            for (String directive : directives) {
                String trimmed = directive.trim();
                if (trimmed.regionMatches(true, 0, "for=", 0, 4)) {
                    addCandidate(candidates, trimmed.substring(4));
                }
            }
        }
    }

    private static void collectCommaSeparatedHeader(List<String> candidates, String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return;
        }
        for (String item : headerValue.split(",")) {
            addCandidate(candidates, item);
        }
    }

    private static void addCandidate(List<String> candidates, String value) {
        String normalized = normalizeIp(value);
        if (StringUtils.hasText(normalized)) {
            candidates.add(normalized);
        }
    }

    private static String normalizeIp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String ip = value.trim();
        if (!StringUtils.hasText(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            return null;
        }

        if (ip.startsWith("\"") && ip.endsWith("\"") && ip.length() > 1) {
            ip = ip.substring(1, ip.length() - 1).trim();
        }

        if (ip.startsWith("[")) {
            int endBracket = ip.indexOf(']');
            if (endBracket > 0) {
                ip = ip.substring(1, endBracket);
            }
        } else if (ip.chars().filter(ch -> ch == ':').count() == 1) {
            int colonIndex = ip.indexOf(':');
            String left = ip.substring(0, colonIndex);
            String right = ip.substring(colonIndex + 1);
            if (left.chars().allMatch(ch -> Character.isDigit(ch) || ch == '.')
                    && right.chars().allMatch(Character::isDigit)) {
                ip = left;
            }
        }

        if (ip.startsWith("::ffff:")) {
            ip = ip.substring(7);
        }

        return StringUtils.hasText(ip) ? ip : null;
    }

    private static boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip);
    }

    private static boolean isPublicIp(String ip) {
        if (!StringUtils.hasText(ip) || isLoopback(ip)) {
            return false;
        }

        String normalized = ip.toLowerCase(Locale.ROOT);
        if (normalized.contains(":")) {
            return !(normalized.startsWith("fc")
                    || normalized.startsWith("fd")
                    || normalized.startsWith("fe80:")
                    || normalized.startsWith("::"));
        }

        if (normalized.startsWith("10.")
                || normalized.startsWith("192.168.")
                || normalized.startsWith("169.254.")) {
            return false;
        }

        if (normalized.startsWith("172.")) {
            String[] parts = normalized.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
                        return false;
                    }
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }

        return true;
    }
}
