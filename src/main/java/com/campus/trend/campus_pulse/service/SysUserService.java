package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SysUser;

import java.util.List;

public interface SysUserService extends IService<SysUser> {

    SysUser searchByUsername(String username);

    List<SysUser> searchByGrade(int grade);

}
