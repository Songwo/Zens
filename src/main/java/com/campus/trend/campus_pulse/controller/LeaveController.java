package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.entity.SysLeaveRequest;
import com.campus.trend.campus_pulse.service.LeaveService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "请假管理")
@RestController
@RequestMapping("/leave")
public class LeaveController {

    @Autowired
    private LeaveService leaveService;

    @Operation(summary = "学生提交请假申请")
    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody SysLeaveRequest request) {
        request.setUserId(SecurityUtils.getCurrentUserId());
        leaveService.submitRequest(request);
        return Result.success();
    }

    @Operation(summary = "获取我的请假记录")
    @GetMapping("/my-list")
    public Result<IPage<SysLeaveRequest>> getMyList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        String userId = SecurityUtils.getCurrentUserId();
        return Result.success(leaveService.getMyRequests(userId, page, pageSize));
    }

    @Operation(summary = "获取待审批列表 (管理员)")
    @GetMapping("/pending")
    public Result<IPage<SysLeaveRequest>> getPending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(leaveService.getPendingRequests(page, pageSize));
    }

    @Operation(summary = "审批请假 (管理员)")
    @PostMapping("/approve")
    public Result<Void> approve(
            @RequestParam Long id,
            @RequestParam Integer status) {
        String adminId = SecurityUtils.getCurrentUserId();
        leaveService.approveRequest(id, adminId, status);
        return Result.success();
    }
}
