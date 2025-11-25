package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.mapper.SysUserMapper;
import com.campus.trend.campus_pulse.service.SysUserService;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {


    @Override
    public List<SysUser> searchByUsername(String username) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysUser::getUsername, username);

        return this.list(wrapper);
    }

}

