package com.campus.trend.campus_pulse.interceptor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 接口性能监控拦截器
 * 记录每个接口的调用次数、响应时间、错误率等指标
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsInterceptor implements HandlerInterceptor {

    private final MeterRegistry meterRegistry;
    private static final String START_TIME_ATTR = "requestStartTime";
    private static final String DURATION_METRIC = "campus.http.server.duration";
    private static final String REQUEST_METRIC = "campus.http.server.requests";
    private static final String ERROR_METRIC = "campus.http.server.errors";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        if (startTime == null) {
            return;
        }

        long duration = System.currentTimeMillis() - startTime;
        String uri = request.getRequestURI();
        String method = request.getMethod();
        int status = response.getStatus();

        // 记录响应时间
        Timer.builder(DURATION_METRIC)
            .tag("uri", simplifyUri(uri))
            .tag("method", method)
            .tag("status", String.valueOf(status))
            .register(meterRegistry)
            .record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);

        // 记录请求计数
        Counter.builder(REQUEST_METRIC)
            .tag("uri", simplifyUri(uri))
            .tag("method", method)
            .tag("status", String.valueOf(status))
            .register(meterRegistry)
            .increment();

        // 慢接口告警
        if (duration > 1000) {
            log.warn("⚠️ 慢接口: {}ms | {} {} | status: {}", duration, method, uri, status);
        }

        // 错误请求计数
        if (status >= 400) {
            Counter.builder(ERROR_METRIC)
                .tag("uri", simplifyUri(uri))
                .tag("method", method)
                .tag("status", String.valueOf(status))
                .register(meterRegistry)
                .increment();
        }
    }

    /**
     * 简化URI，避免ID等动态参数导致指标爆炸
     * /api/posts/123 -> /api/posts/{id}
     */
    private String simplifyUri(String uri) {
        if (uri == null) {
            return "unknown";
        }

        // 替换UUID格式的ID
        uri = uri.replaceAll("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "/{id}");

        // 替换数字ID
        uri = uri.replaceAll("/\\d+", "/{id}");

        // 替换POST开头的自定义ID
        uri = uri.replaceAll("/POST[A-Z0-9]+", "/{id}");

        return uri;
    }
}
