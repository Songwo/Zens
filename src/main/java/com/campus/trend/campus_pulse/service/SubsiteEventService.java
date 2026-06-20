package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.SubsiteEventCreateReq;
import com.campus.trend.campus_pulse.dto.response.SimplePageResp;
import com.campus.trend.campus_pulse.dto.response.SubsiteEventResp;

public interface SubsiteEventService {

    SubsiteEventResp record(SubsiteEventCreateReq req, String serviceId);

    SimplePageResp<SubsiteEventResp> pageMy(String userId, int page, int pageSize, String source);

    SimplePageResp<SubsiteEventResp> pageAdmin(int page,
                                               int pageSize,
                                               String source,
                                               String eventType,
                                               String userId,
                                               String status);
}
