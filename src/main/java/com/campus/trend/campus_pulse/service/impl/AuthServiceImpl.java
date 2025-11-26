package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.dto.LoginRequest;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.SysUserService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authorizationManager;

    private final SysUserService sysUserService;

    public AuthServiceImpl(AuthenticationManager authorizationManager, SysUserService sysUserService) {
        this.authorizationManager = authorizationManager;
        this.sysUserService = sysUserService;
    }

    @Override
    public String login(LoginRequest loginRequest) {
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword());
        Authentication authentication = authorizationManager.authenticate(token);

        return "";
    }

}
