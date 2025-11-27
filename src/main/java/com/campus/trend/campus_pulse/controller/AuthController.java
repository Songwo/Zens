package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.dto.request.LoginRequest;
import com.campus.trend.campus_pulse.dto.request.RegisterRequest;
import com.campus.trend.campus_pulse.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    private final AuthService authService;

    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        return Result.success(authService.login(loginRequest));
    }

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.register(registerRequest);
        return Result.success();
    }



}
