package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.config.properties.OpsAutomationProperties;
import com.campus.trend.campus_pulse.dto.request.*;
import com.campus.trend.campus_pulse.service.OpsAutomationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/ops")
@RequiredArgsConstructor
public class InternalOpsController {
  private final OpsAutomationService service;
  private final OpsAutomationProperties props;

  @PostMapping("/drafts")
  public Result<?> create(
      @Valid @RequestBody OpsDraftCreateReq r, @RequestAttribute("internal.serviceId") String sid) {
    return Result.success(service.createDraft(r, sid));
  }

  @PostMapping("/drafts/{id}/submit")
  public Result<?> submit(
      @PathVariable String id, @RequestAttribute("internal.serviceId") String sid) {
    return Result.success(service.submit(id, sid));
  }

  @PostMapping("/drafts/{id}/publish")
  public Result<?> publish(
      @PathVariable String id,
      @Valid @RequestBody OpsPublishReq r,
      @RequestAttribute("internal.serviceId") String sid) {
    return Result.success(service.publish(id, r.getIdempotencyKey(), sid));
  }

  @PostMapping("/plans")
  public Result<?> plan(
      @Valid @RequestBody OpsPlanCreateReq r, @RequestAttribute("internal.serviceId") String sid) {
    return Result.success(service.createPlan(r, sid));
  }

  @PostMapping("/metrics")
  public Result<?> metric(
      @Valid @RequestBody OpsMetricReq r, @RequestAttribute("internal.serviceId") String sid) {
    return Result.success(service.recordMetric(r, sid));
  }

  @GetMapping("/status")
  public Result<?> status(@RequestAttribute("internal.serviceId") String sid) {
    if (!props.getServiceId().equals(sid)) throw new BusinessException(ResultCode.NO_PERMISSION);
    return Result.success(service.status());
  }
}
