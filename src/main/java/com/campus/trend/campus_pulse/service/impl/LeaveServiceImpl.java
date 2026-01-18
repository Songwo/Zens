package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.entity.SysLeaveRequest;
import com.campus.trend.campus_pulse.mapper.SysLeaveRequestMapper;
import com.campus.trend.campus_pulse.service.LeaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LeaveServiceImpl implements LeaveService {

    @Autowired
    private SysLeaveRequestMapper leaveRequestMapper;

    @Override
    public void submitRequest(SysLeaveRequest request) {
        request.setStatus(0); // 待审批
        request.setCreateTime(LocalDateTime.now());
        leaveRequestMapper.insert(request);
    }

    @Override
    public IPage<SysLeaveRequest> getMyRequests(String userId, int page, int pageSize) {
        Page<SysLeaveRequest> p = new Page<>(page, pageSize);
        return leaveRequestMapper.selectPage(p, new LambdaQueryWrapper<SysLeaveRequest>()
                .eq(SysLeaveRequest::getUserId, userId)
                .orderByDesc(SysLeaveRequest::getCreateTime));
    }

    @Override
    public IPage<SysLeaveRequest> getPendingRequests(int page, int pageSize) {
        Page<SysLeaveRequest> p = new Page<>(page, pageSize);
        return leaveRequestMapper.selectPage(p, new LambdaQueryWrapper<SysLeaveRequest>()
                .eq(SysLeaveRequest::getStatus, 0));
    }

    @Override
    public void approveRequest(Long requestId, String adminId, Integer status) {
        SysLeaveRequest request = leaveRequestMapper.selectById(requestId);
        if (request != null) {
            request.setStatus(status);
            request.setApproverId(adminId);
            request.setApproveTime(LocalDateTime.now());
            leaveRequestMapper.updateById(request);
        }
    }
}
