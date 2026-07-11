package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.SupporterOrderCreateReq;
import com.campus.trend.campus_pulse.dto.response.PaymentOrderResp;
import com.campus.trend.campus_pulse.dto.response.SupporterPlanResp;
import com.campus.trend.campus_pulse.dto.response.SupporterStatusResp;

import java.util.List;
import java.util.Map;

public interface SupporterPaymentService {
    List<SupporterPlanResp> listPlans();
    PaymentOrderResp createOrder(String userId, SupporterOrderCreateReq request);
    PaymentOrderResp getOrder(String userId, String orderNo);
    SupporterStatusResp getCurrentStatus(String userId);
    void handleCallback(String provider, String rawBody, Map<String, String> headers);
}
