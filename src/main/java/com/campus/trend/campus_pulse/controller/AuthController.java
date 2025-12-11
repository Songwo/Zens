package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.dto.request.LoginRequest;
import com.campus.trend.campus_pulse.dto.request.RegisterRequest;
import com.campus.trend.campus_pulse.dto.request.SendCodeRequest;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.VerificationCodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final VerificationCodeService verificationCodeService;

    public AuthController(AuthService authService,
            VerificationCodeService verificationCodeService) {
        this.authService = authService;
        this.verificationCodeService = verificationCodeService;
    }

    @PostMapping("/send-code")
    public Result<?> sendCode(@Valid @RequestBody SendCodeRequest request) {
        verificationCodeService.sendCode(request.getEmail());
        return Result.success("验证码已发送");
    }

    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        return Result.success(authService.Login(loginRequest));
    }

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.Register(registerRequest);
        return Result.success();
    }

    @PostMapping("/logout")
    public Result<?> logout() {
        authService.Logout();
        return Result.success();
    }

}
