package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.SupporterFeedbackCreateReq;
import com.campus.trend.campus_pulse.dto.request.SupporterFeedbackReplyReq;
import com.campus.trend.campus_pulse.dto.response.SimplePageResp;
import com.campus.trend.campus_pulse.dto.response.SupporterFeedbackResp;

public interface SupporterFeedbackService {
    SupporterFeedbackResp create(String userId, SupporterFeedbackCreateReq request);
    SimplePageResp<SupporterFeedbackResp> pageMine(String userId, int page, int pageSize);
    SimplePageResp<SupporterFeedbackResp> pageAdmin(int page, int pageSize, String status, String userId);
    SupporterFeedbackResp reply(Long id, String adminId, SupporterFeedbackReplyReq request);
}
