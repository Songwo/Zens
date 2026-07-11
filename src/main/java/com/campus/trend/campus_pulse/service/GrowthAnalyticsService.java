package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.GrowthEventReq;

import java.util.Map;

public interface GrowthAnalyticsService {
    void record(GrowthEventReq request, String userId);
    Map<String, Object> dashboard(int days);
}
