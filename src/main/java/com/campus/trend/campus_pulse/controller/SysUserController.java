package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.dto.LoginRequest;
import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.SysUserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys-user")
public class SysUserController {

    public SysUserController(AuthService authService){
        this.authService = authService;
    }

    private final AuthService authService;

    @GetMapping("/test")
    public String Test(){
        return "test";
    }


    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest loginRequest){
        String jwt = authService.login(loginRequest);
        return Map.of(
            "message","Success Login",
            "data",jwt
        );
    }
}
