package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.dto.response.ReportManageResp;
import com.campus.trend.campus_pulse.entity.SysReport;

public interface SysReportService extends IService<SysReport> {

    IPage<ReportManageResp> getManagePage(String userId, Integer current, Integer size, Integer status);

    SysReport getAccessibleReport(String userId, String reportId);
}
