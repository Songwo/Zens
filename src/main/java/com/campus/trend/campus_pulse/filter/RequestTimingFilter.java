package com.campus.trend.campus_pulse.filter;

import com.campus.trend.campus_pulse.monitor.PerformanceEventRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class RequestTimingFilter extends OncePerRequestFilter {

    private final PerformanceEventRecorder performanceEventRecorder;

    @Value("${campus.observability.request-log-enabled:true}")
    private boolean requestLogEnabled;

    @Value("${campus.observability.slow-request-ms:1000}")
    private long slowRequestMs;

    @Value("${campus.observability.request-log-exclude-prefixes:/actuator,/static,/uploads}")
    private String excludePrefixesRaw;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!requestLogEnabled || shouldSkip(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            response.setHeader("X-Response-Time-Ms", String.valueOf(elapsed));
            if (elapsed >= Math.max(100L, slowRequestMs)) {
                performanceEventRecorder.recordSlowRequest(
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        elapsed);
                log.warn("慢请求: method={}, uri={}, status={}, cost={}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        elapsed);
            } else {
                log.debug("请求耗时: method={}, uri={}, status={}, cost={}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        elapsed);
            }
        }
    }

    private boolean shouldSkip(String uri) {
        if (!StringUtils.hasText(uri)) {
            return true;
        }
        List<String> prefixes = Arrays.stream(excludePrefixesRaw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        for (String prefix : prefixes) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
