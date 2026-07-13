package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.annotation.RateLimit;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.SupporterVoucherImportReq;
import com.campus.trend.campus_pulse.dto.response.SupporterVoucherGrantResp;
import com.campus.trend.campus_pulse.dto.response.SupporterVoucherImportResp;
import com.campus.trend.campus_pulse.dto.response.SupporterVoucherInventoryResp;
import com.campus.trend.campus_pulse.service.SupporterVoucherService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SupporterVoucherController {
    private final SupporterVoucherService voucherService;

    @GetMapping("/supporter/vouchers")
    @RateLimit(key = "supporter_vouchers_mine", limit = 60, windowSeconds = 60)
    public ResponseEntity<Result<List<SupporterVoucherGrantResp>>> mine() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.success(voucherService.listMine(SecurityUtils.getCurrentUserId())));
    }

    @PostMapping({"/admin/supporter-vouchers/import", "/api/admin/supporter-vouchers/import"})
    @RateLimit(key = "supporter_vouchers_import", limit = 10, windowSeconds = 60)
    public Result<SupporterVoucherImportResp> importCodes(@Valid @RequestBody SupporterVoucherImportReq request) {
        return Result.success(voucherService.importCodes(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping({"/admin/supporter-vouchers/inventory", "/api/admin/supporter-vouchers/inventory"})
    public Result<List<SupporterVoucherInventoryResp>> inventory() {
        return Result.success(voucherService.inventory());
    }
}
