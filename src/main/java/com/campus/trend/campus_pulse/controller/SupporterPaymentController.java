package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.SupporterOrderCreateReq;
import com.campus.trend.campus_pulse.dto.response.PaymentOrderResp;
import com.campus.trend.campus_pulse.dto.response.SupporterPlanResp;
import com.campus.trend.campus_pulse.dto.response.SupporterStatusResp;
import com.campus.trend.campus_pulse.service.SupporterPaymentService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SupporterPaymentController {
    private final SupporterPaymentService service;

    @GetMapping("/supporter/plans")
    public Result<List<SupporterPlanResp>> plans() {
        return Result.success(service.listPlans());
    }

    @GetMapping("/supporter/me")
    public Result<SupporterStatusResp> me() {
        return Result.success(service.getCurrentStatus(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/supporter/orders")
    public Result<PaymentOrderResp> createOrder(@Valid @RequestBody SupporterOrderCreateReq request) {
        return Result.success(service.createOrder(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/supporter/orders/{orderNo}")
    public Result<PaymentOrderResp> order(@PathVariable String orderNo) {
        return Result.success(service.getOrder(SecurityUtils.getCurrentUserId(), orderNo));
    }

    @PostMapping(value = "/payment/callback/{provider}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<String> callback(@PathVariable String provider,
                                           @RequestBody String rawBody,
                                           @RequestHeader Map<String, String> headers) {
        Map<String, String> normalizedHeaders = new LinkedHashMap<>();
        headers.forEach((key, value) -> normalizedHeaders.put(key.toLowerCase(Locale.ROOT), value));
        service.handleCallback(provider, rawBody, normalizedHeaders);
        return ResponseEntity.ok("success");
    }
}
