package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.entity.SysLeaveRequest;

public interface LeaveService {
    // 提交请假
    void submitRequest(SysLeaveRequest request);

    // 获取我的请假列表
    IPage<SysLeaveRequest> getMyRequests(String userId, int page, int pageSize);

    // 获取待审批列表 (管理员)
    IPage<SysLeaveRequest> getPendingRequests(int page, int pageSize);

    // 审批 (管理员)
    void approveRequest(Long requestId, String adminId, Integer status);
}
