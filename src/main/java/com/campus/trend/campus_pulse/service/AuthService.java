package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.LoginRequest;
import com.campus.trend.campus_pulse.dto.request.RegisterRequest;
import com.campus.trend.campus_pulse.dto.response.LoginResponse;
import com.campus.trend.campus_pulse.dto.response.ProFileResponse;
import com.campus.trend.campus_pulse.dto.response.SimpleProfileResponse;
import com.campus.trend.campus_pulse.entity.SysUser;

import java.util.List;


public interface AuthService {

    LoginResponse login(LoginRequest loginRequest);

    void register(RegisterRequest registerRequest);


    //TODO 待实现列表
    // 1.用户获取详细个人信息
    // 2.获取简单个人信息
    // 3.用户头像上传
    // 4.根据用户ID改用户密码
    // 5.根据用户名修改用户信息
    // 6.年级设置一年自动更新

}
