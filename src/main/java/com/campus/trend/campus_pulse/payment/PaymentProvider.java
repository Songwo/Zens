package com.campus.trend.campus_pulse.payment;

import com.campus.trend.campus_pulse.entity.PaymentOrder;

import java.util.Map;

public interface PaymentProvider {
    String code();

    PaymentCheckout createCheckout(PaymentOrder order);

    PaymentNotification verifyCallback(String rawBody, Map<String, String> headers);
}
