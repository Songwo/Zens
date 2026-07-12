package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.SupporterCreatorInsightsResp;

public interface SupporterCreatorInsightsService {
    SupporterCreatorInsightsResp getInsights(String userId, int days);
}
