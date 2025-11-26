package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.dto.LoginRequest;
import com.campus.trend.campus_pulse.dto.RegisterRequest;
import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.SysUserService;
import com.campus.trend.campus_pulse.utils.GenerateIDUtil;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authorizationManager;

    private final SysUserService sysUserService;

    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(AuthenticationManager authorizationManager,
                           SysUserService sysUserService,
                           PasswordEncoder passwordEncoder) {
        this.authorizationManager = authorizationManager;
        this.sysUserService = sysUserService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String login(LoginRequest loginRequest) {
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword());
        Authentication authentication = authorizationManager.authenticate(token);

        AuthSysUser User = (AuthSysUser) authentication.getPrincipal();

        SysUser sysUser = User.getSysUser();
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", sysUser.getUsername());
        claims.put("avatar", sysUser.getAvatar());

        return JwtUtil.GenerateToken(sysUser.getId(), claims);
    }

    @Override
    public boolean register(RegisterRequest registerRequest) {

        SysUser sysUser = new SysUser();
        String op = "USER";
        sysUser.setId(GenerateIDUtil.genId(op));
        sysUser.setUsername(registerRequest.getUsername());

        sysUser.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        sysUser.setAvatar(registerRequest.getAvatar());
        sysUser.setMajor(registerRequest.getMajor());
        sysUser.setGrade(registerRequest.getGrade());
        sysUser.setNickname(registerRequest.getNickname());

        return sysUserService.save(sysUser);
    }

}
