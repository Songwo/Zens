package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.dto.request.LoginRequest;
import com.campus.trend.campus_pulse.dto.request.RegisterRequest;
import com.campus.trend.campus_pulse.dto.request.SendCodeRequest;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.VerificationCodeService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
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

    @GetMapping(value = "/captcha", produces = "image/png")
    public void getCaptcha(String uuid, HttpServletResponse response) throws IOException {
        if (uuid == null || uuid.isEmpty()) {
            response.setStatus(400);
            return;
        }
        response.setContentType("image/png");
        response.setHeader("Pragma", "No-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);

        try {
            cn.hutool.captcha.ICaptcha captcha = verificationCodeService.getCaptcha(uuid);
            captcha.write(response.getOutputStream());
        } catch (Exception e) {
            log.error("验证码生成失败 error processing captcha", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Captcha generation failed");
        }
    }

    @PostMapping("/send-code")
    public Result<?> sendCode(@Valid @RequestBody SendCodeRequest request) {
        verificationCodeService.sendCode(request.getEmail());
        return Result.success("验证码已发送");
    }

    @PostMapping("/send-login-code") // 废弃，但保留兼容
    public Result<?> sendLoginCode(@RequestBody SendCodeRequest request) {
        verificationCodeService.sendLoginCode(request.getEmail());
        return Result.success("登录验证码已发送");
    }

    @GetMapping("/login-fail-count/{username}")
    public Result<?> getLoginFailCount(@PathVariable String username) {
        // 返回是否被锁定以及剩余时间
        boolean locked = verificationCodeService.isLocked(username);
        long timeLeft = verificationCodeService.getLockTimeLeft(username);
        return Result.success(java.util.Map.of("locked", locked, "timeLeft", timeLeft));
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
