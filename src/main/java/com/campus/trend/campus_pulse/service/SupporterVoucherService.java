package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.SupporterVoucherImportReq;
import com.campus.trend.campus_pulse.dto.response.SupporterVoucherGrantResp;
import com.campus.trend.campus_pulse.dto.response.SupporterVoucherImportResp;
import com.campus.trend.campus_pulse.dto.response.SupporterVoucherInventoryResp;
import com.campus.trend.campus_pulse.entity.PaymentOrder;

import java.time.LocalDateTime;
import java.util.List;

public interface SupporterVoucherService {
    void createGrantAndTryIssue(PaymentOrder order, LocalDateTime grantedAt);
    List<SupporterVoucherGrantResp> listMine(String userId);
    SupporterVoucherImportResp importCodes(String adminId, SupporterVoucherImportReq request);
    List<SupporterVoucherInventoryResp> inventory();
}
