package com.campus.trend.campus_pulse.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 生态集成层指标:SSO 授权、内部 s2s API、子站事件账本、积分增减。
 *
 * 标签均为低基数(clientId/serviceId/source/eventType/op/result 都是有限枚举),
 * 不会造成指标爆炸。通过 /actuator/prometheus 暴露。
 */
@Component
@RequiredArgsConstructor
public class EcosystemMetrics {

    private final MeterRegistry meterRegistry;

    /** SSO 授权颁发:result = success / not_login / invalid_client / user_not_found / error */
    public void recordSsoAuthorize(String clientId, String result) {
        Counter.builder("zens.sso.authorize")
                .tag("client_id", safe(clientId))
                .tag("result", safe(result))
                .register(meterRegistry)
                .increment();
    }

    /** 内部 s2s API 调用:result = ok / missing_headers / unknown_service / bad_signature / ... */
    public void recordInternalApiCall(String serviceId, String result) {
        Counter.builder("zens.internal.api.calls")
                .tag("service", safe(serviceId))
                .tag("result", safe(result))
                .register(meterRegistry)
                .increment();
    }

    /** 子站事件入账:source = zdc-shop / campus-lottery-station / ...；type = lottery.draw.win / ... */
    public void recordSubsiteEvent(String source, String eventType) {
        Counter.builder("zens.subsite.event")
                .tag("source", safe(source))
                .tag("type", safe(eventType))
                .register(meterRegistry)
                .increment();
    }

    /** 积分增减:op = consume / credit；result = success / insufficient / not_found / error */
    public void recordPointsMutation(String op, String result) {
        Counter.builder("zens.points.mutation")
                .tag("op", safe(op))
                .tag("result", safe(result))
                .register(meterRegistry)
                .increment();
    }

    private static String safe(String v) {
        return StringUtils.hasText(v) ? v : "unknown";
    }
}
