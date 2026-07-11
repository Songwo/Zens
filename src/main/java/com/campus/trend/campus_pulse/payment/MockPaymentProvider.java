package com.campus.trend.campus_pulse.payment;

import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.config.properties.PaymentProperties;
import com.campus.trend.campus_pulse.entity.PaymentOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

/** 仅用于本地/测试的签名回调渠道，生产配置会在启动期被拒绝。 */
@Component
public class MockPaymentProvider implements PaymentProvider {
    private static final long CALLBACK_WINDOW_SECONDS = 300;
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    public MockPaymentProvider(PaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String code() {
        return "mock";
    }

    @Override
    public PaymentCheckout createCheckout(PaymentOrder order) {
        requireEnabled();
        return new PaymentCheckout("mock_" + order.getOrderNo(), null);
    }

    @Override
    public PaymentNotification verifyCallback(String rawBody, Map<String, String> headers) {
        requireEnabled();
        String timestamp = headers.get("x-payment-timestamp");
        String signature = headers.get("x-payment-signature");
        if (timestamp == null || signature == null) {
            throw invalidCallback();
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw invalidCallback();
        }
        if (Math.abs(Instant.now().getEpochSecond() - epochSeconds) > CALLBACK_WINDOW_SECONDS) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "支付回调已过期");
        }
        String expected = hmac(timestamp + "\n" + rawBody, properties.getMockWebhookSecret());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            throw invalidCallback();
        }
        try {
            JsonNode json = objectMapper.readTree(rawBody);
            String status = json.path("status").asText();
            if (!"PAID".equals(status)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "暂不支持的支付事件状态");
            }
            long paidAtEpoch = json.path("paidAt").asLong(epochSeconds);
            return new PaymentNotification(
                    requiredText(json, "eventId"),
                    requiredText(json, "orderNo"),
                    requiredText(json, "providerOrderNo"),
                    status,
                    json.path("amountCents").asInt(-1),
                    requiredText(json, "currency"),
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(paidAtEpoch), ZoneOffset.UTC));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "支付回调内容无效");
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || !properties.isAllowMock() || !"mock".equals(properties.getProvider())) {
            throw new BusinessException(ResultCode.FAILED, "测试支付渠道未启用");
        }
    }

    private String requiredText(JsonNode json, String field) {
        String value = json.path(field).asText();
        if (value.isBlank()) throw new BusinessException(ResultCode.PARAM_ERROR, "缺少支付字段: " + field);
        return value;
    }

    private String hmac(String content, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成支付回调签名", e);
        }
    }

    private BusinessException invalidCallback() {
        return new BusinessException(ResultCode.TOKEN_INVALID, "支付回调签名无效");
    }
}
