package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.dto.request.SubsiteEventCreateReq;
import com.campus.trend.campus_pulse.dto.response.SimplePageResp;
import com.campus.trend.campus_pulse.dto.response.SubsiteEventResp;
import com.campus.trend.campus_pulse.service.SubsiteEventService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SubsiteEventController {

    private final SubsiteEventService subsiteEventService;

    @PostMapping("/api/internal/subsite/events")
    public Result<SubsiteEventResp> record(@Valid @RequestBody SubsiteEventCreateReq req,
                                           HttpServletRequest request) {
        String serviceId = (String) request.getAttribute("internal.serviceId");
        return Result.success(subsiteEventService.record(req, serviceId));
    }

    @GetMapping({"/subsite-events/my", "/api/subsite-events/my"})
    public Result<SimplePageResp<SubsiteEventResp>> my(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int pageSize,
                                                       @RequestParam(required = false) String source) {
        return Result.success(subsiteEventService.pageMy(
                SecurityUtils.getCurrentUserId(),
                page,
                pageSize,
                source
        ));
    }

    @GetMapping({"/admin/subsite-events", "/api/admin/subsite-events"})
    public Result<SimplePageResp<SubsiteEventResp>> admin(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int pageSize,
                                                          @RequestParam(required = false) String source,
                                                          @RequestParam(required = false) String eventType,
                                                          @RequestParam(required = false) String userId,
                                                          @RequestParam(required = false) String status) {
        if (!PermissionUtils.isAdmin()) {
            return Result.error(ResultCode.NO_PERMISSION, "仅管理员可查看子系统事件账本");
        }
        return Result.success(subsiteEventService.pageAdmin(page, pageSize, source, eventType, userId, status));
    }
}
