package com.campus.trend.campus_pulse.payment;

import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.config.properties.PaymentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockPaymentProviderTest {
    private static final String SECRET = "local-test-secret-at-least-32-characters";
    private MockPaymentProvider provider;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(new MockEnvironment());
        properties.setEnabled(true);
        properties.setProvider("mock");
        properties.setAllowMock(true);
        properties.setMockWebhookSecret(SECRET);
        provider = new MockPaymentProvider(properties, new ObjectMapper());
    }

    @Test
    void verifiesSignedCallbackAndParsesTrustedFields() {
        long timestamp = Instant.now().getEpochSecond();
        String body = "{\"eventId\":\"evt_1\",\"orderNo\":\"ZS001\","
                + "\"providerOrderNo\":\"mock_ZS001\",\"status\":\"PAID\","
                + "\"amountCents\":900,\"currency\":\"CNY\",\"paidAt\":" + timestamp + "}";

        PaymentNotification notification = provider.verifyCallback(body, Map.of(
                "x-payment-timestamp", String.valueOf(timestamp),
                "x-payment-signature", sign(timestamp, body)));

        assertEquals("evt_1", notification.eventId());
        assertEquals("ZS001", notification.orderNo());
        assertEquals(900, notification.amountCents());
        assertEquals("CNY", notification.currency());
    }

    @Test
    void rejectsInvalidSignature() {
        long timestamp = Instant.now().getEpochSecond();
        assertThrows(BusinessException.class, () -> provider.verifyCallback("{}", Map.of(
                "x-payment-timestamp", String.valueOf(timestamp),
                "x-payment-signature", "00".repeat(32))));
    }

    @Test
    void rejectsExpiredCallbackEvenWithValidSignature() {
        long timestamp = Instant.now().minusSeconds(301).getEpochSecond();
        String body = "{}";
        assertThrows(BusinessException.class, () -> provider.verifyCallback(body, Map.of(
                "x-payment-timestamp", String.valueOf(timestamp),
                "x-payment-signature", sign(timestamp, body))));
    }

    private String sign(long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "\n" + body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
