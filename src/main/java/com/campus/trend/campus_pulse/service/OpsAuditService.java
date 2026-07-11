package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.entity.OpsJobRun;

public interface OpsAuditService {
  OpsJobRun startPublish(
      String idempotencyKey, String draftId, String serviceId, String operatorId);

  void finishPublish(String jobId, String status, String errorMessage);
}
