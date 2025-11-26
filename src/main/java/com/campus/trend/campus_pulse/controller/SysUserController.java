package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.common.ResultCode;
import com.campus.trend.campus_pulse.dto.LoginRequest;
import com.campus.trend.campus_pulse.dto.RegisterRequest;
import com.campus.trend.campus_pulse.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/sys-user")
public class SysUserController {

    public SysUserController(AuthService authService) {
        this.authService = authService;
    }

    private final AuthService authService;

    @GetMapping("/test")
    public String Test() {
        return "test";
    }


    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            String jwt = authService.login(loginRequest);
            return Result.success(jwt);
        } catch (Exception e) {
            return Result.error(ResultCode.LOGIN_ERROR, e.getMessage());
        }
    }

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.register(registerRequest);
        return Result.success();
    }
}
