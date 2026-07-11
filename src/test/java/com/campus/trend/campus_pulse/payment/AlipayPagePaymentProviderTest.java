package com.campus.trend.campus_pulse.payment;

import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.config.properties.PaymentProperties;
import com.campus.trend.campus_pulse.entity.PaymentOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class AlipayPagePaymentProviderTest {
    private KeyPair keyPair;
    private AlipayPagePaymentProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        PaymentProperties properties = new PaymentProperties(new MockEnvironment());
        properties.setEnabled(true);
        properties.setProvider("alipay");
        properties.getAlipay().setAppId("2026000000000001");
        properties.getAlipay().setMerchantPrivateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        properties.getAlipay().setAlipayPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        properties.getAlipay().setNotifyUrl("https://www.allinsong.top/api/payment/callback/alipay");
        properties.getAlipay().setReturnUrl("https://www.allinsong.top/supporter");
        provider = new AlipayPagePaymentProvider(properties, new ObjectMapper());
    }

    @Test
    void createsRsa2SignedPagePayCheckout() throws Exception {
        PaymentCheckout checkout = provider.createCheckout(PaymentOrder.builder()
                .orderNo("ZS20260711001").planNameSnapshot("Zens 支持者")
                .amountCents(900).build());
        assertNotNull(checkout.checkoutUrl());
        Map<String, String> params = parseQuery(checkout.checkoutUrl().substring(checkout.checkoutUrl().indexOf('?') + 1));
        assertEquals("alipay.trade.page.pay", params.get("method"));
        assertTrue(params.get("biz_content").contains("\"total_amount\":\"9.00\""));
        assertTrue(verify(content(params, false), params.get("sign")));
    }

    @Test
    void verifiesSignedPaidNotificationAndRejectsTampering() throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("app_id", "2026000000000001");
        fields.put("trade_no", "202607112200001234");
        fields.put("out_trade_no", "ZS20260711001");
        fields.put("trade_status", "TRADE_SUCCESS");
        fields.put("total_amount", "9.00");
        fields.put("gmt_payment", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        fields.put("sign_type", "RSA2");
        fields.put("sign", sign(content(fields, true)));

        PaymentNotification notification = provider.verifyCallback(form(fields), Map.of());
        assertEquals(900, notification.amountCents());
        assertEquals("ZS20260711001", notification.orderNo());

        fields.put("total_amount", "19.00");
        assertThrows(BusinessException.class, () -> provider.verifyCallback(form(fields), Map.of()));
    }

    private String content(Map<String, String> params, boolean callback) {
        List<String> pairs = new ArrayList<>();
        new TreeMap<>(params).forEach((key, value) -> {
            if ("sign".equals(key) || (callback && "sign_type".equals(key)) || value == null || value.isBlank()) return;
            pairs.add(key + "=" + value);
        });
        return String.join("&", pairs);
    }

    private String sign(String value) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private boolean verify(String value, String signature) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(value.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getDecoder().decode(signature));
    }

    private String form(Map<String, String> fields) {
        return fields.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((a, b) -> a + "&" + b).orElse("");
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(decode(parts[0]), decode(parts.length > 1 ? parts[1] : ""));
        }
        return result;
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
}
