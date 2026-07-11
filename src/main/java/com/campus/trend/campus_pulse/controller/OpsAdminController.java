package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.*;
import com.campus.trend.campus_pulse.service.OpsAutomationService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops-admin")
@RequiredArgsConstructor
public class OpsAdminController {
  private final OpsAutomationService service;

  @GetMapping("/drafts")
  public Result<?> drafts(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(service.list(status, page, size));
  }

  @GetMapping("/status")
  public Result<?> status() {
    return Result.success(service.status());
  }

  @PostMapping("/drafts/{id}/approve")
  public Result<?> approve(@PathVariable String id, @Valid @RequestBody OpsDecisionReq r) {
    return Result.success(service.approve(id, SecurityUtils.getCurrentUserId(), r.getNote()));
  }

  @PostMapping("/drafts/{id}/reject")
  public Result<?> reject(@PathVariable String id, @Valid @RequestBody OpsDecisionReq r) {
    return Result.success(service.reject(id, SecurityUtils.getCurrentUserId(), r.getNote()));
  }

  @PostMapping("/drafts/{id}/publish")
  public Result<?> publish(@PathVariable String id, @Valid @RequestBody OpsPublishReq r) {
    return Result.success(
        service.publishByAdmin(id, r.getIdempotencyKey(), SecurityUtils.getCurrentUserId()));
  }

  @PostMapping("/circuit")
  public Result<?> circuit(@Valid @RequestBody OpsCircuitReq r) {
    return Result.success(
        service.setCircuit(r.isOpen(), r.getReason(), SecurityUtils.getCurrentUserId()));
  }
}
