package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.LoginRequest;
import com.campus.trend.campus_pulse.dto.RegisterRequest;
import com.campus.trend.campus_pulse.entity.SysUser;

import java.util.List;


public interface AuthService {

    String login(LoginRequest loginRequest);

    void register(RegisterRequest registerRequest);

    List<SysUser> getUsers();
}
