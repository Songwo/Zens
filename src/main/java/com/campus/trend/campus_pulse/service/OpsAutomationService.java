package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.dto.request.*;
import com.campus.trend.campus_pulse.entity.*;
import java.util.Map;

public interface OpsAutomationService {
  OpsDraft createDraft(OpsDraftCreateReq req, String serviceId);

  OpsDraft submit(String id, String serviceId);

  IPage<OpsDraft> list(String status, int page, int size);

  OpsDraft approve(String id, String operator, String note);

  OpsDraft reject(String id, String operator, String note);

  OpsDraft publish(String id, String idempotencyKey, String serviceId);

  OpsContentPlan createPlan(OpsPlanCreateReq req, String serviceId);

  OpsDraft publishByAdmin(String id, String idempotencyKey, String operatorId);

  OpsMetricSnapshot recordMetric(OpsMetricReq req, String serviceId);

  Map<String, Object> status();

  Map<String, Object> setCircuit(boolean open, String reason, String operator);
}
