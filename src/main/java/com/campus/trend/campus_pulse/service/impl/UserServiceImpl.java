package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.dto.response.ProFileResponse;
import com.campus.trend.campus_pulse.dto.response.SimpleProfileResponse;
import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.service.mapperservice.SysUserService;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {


    private final SysUserService sysUserService;


    public UserServiceImpl(SysUserService sysUserService) {
        this.sysUserService = sysUserService;

    }

    @Override
    public ProFileResponse getProFile() {
        // 1.从Security上下文中获取用户信息
        AuthSysUser auUser = GetUserDetail.getAuthenticatedUser();

        // 2.获取用户详细信息
        SysUser sysUser = sysUserService.searchByUsername(auUser.getUsername());
        // 3.构造用户信息响应
        ProFileResponse proFileResponse = new ProFileResponse();
        proFileResponse.setUsername(sysUser.getUsername());
        proFileResponse.setAvatar(sysUser.getAvatar());
        proFileResponse.setNickname(sysUser.getNickname());
        proFileResponse.setMajor(sysUser.getMajor());
        proFileResponse.setGrade(sysUser.getGrade());
        proFileResponse.setInterest_tags(sysUser.getInterestTags());
        proFileResponse.setCreatTime(sysUser.getCreateTime());

        return proFileResponse;
    }

    @Override
    public SimpleProfileResponse getSimpleProfile() {
        // 1.从Security上下文中获取用户信息
        AuthSysUser auUser = GetUserDetail.getAuthenticatedUser();

        // 2.获取用户详细信息
        SysUser sysUser = sysUserService.searchByUsername(auUser.getUsername());
        // 3.构造用户信息响应
        SimpleProfileResponse simpleProfileResponse = new SimpleProfileResponse();
        simpleProfileResponse.setAvatar(sysUser.getAvatar());
        simpleProfileResponse.setNickname(sysUser.getNickname());
        simpleProfileResponse.setInterest_tags(sysUser.getInterestTags());

        return simpleProfileResponse;
    }

    @Override
    public List<SysUser> getUsers() {
        return sysUserService.searchAll();
    }

}
