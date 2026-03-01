package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.SysReport;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import com.campus.trend.campus_pulse.service.SysReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Song：举报管理控制器
 */
@Tag(name = "举报管理")
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final SysReportService sysReportService;

    /**
     * Song：提交举报
     * Song：说明
     */
    @Operation(summary = "提交举报")
    @PostMapping("/create")
    public Result<?> createReport(@RequestBody SysReport report) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        report.setReporterId(authUser.getUser().getId());
        report.setStatus(0); // Song：待处理
        report.setCreateTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        sysReportService.save(report);
        return Result.success("举报提交成功");
    }

    /**
     * Song：获取举报列表 (管理员)
     * Song：说明
     */
    @Operation(summary = "获取举报列表")
    @GetMapping("/list")
    public Result<IPage<SysReport>> getReportList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {

        Page<SysReport> page = new Page<>(current, size);
        QueryWrapper<SysReport> wrapper = new QueryWrapper<>();

        if (status != null) {
            wrapper.eq("status", status);
        }

        wrapper.orderByDesc("create_time");

        IPage<SysReport> result = sysReportService.page(page, wrapper);
        return Result.success(result);
    }

    /**
     * Song：标记举报为已处理
     * Song：说明
     */
    @Operation(summary = "处理举报")
    @PostMapping("/resolve/{id}")
    public Result<?> resolveReport(@PathVariable String id, @RequestParam Integer status) {
        SysReport report = sysReportService.getById(id);
        if (report == null) {
            return Result.failed("举报记录不存在");
        }
        report.setStatus(status);
        report.setUpdateTime(LocalDateTime.now());
        sysReportService.updateById(report);
        return Result.success();
    }
}
