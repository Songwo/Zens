package com.campus.trend.campus_pulse.service;

public interface ReportWorkflowService {

    void queueResolve(String reportId, Integer finalStatus, String operatorUserId);

    void queueRejectPost(String reportId, String reason, String operatorUserId);
}
