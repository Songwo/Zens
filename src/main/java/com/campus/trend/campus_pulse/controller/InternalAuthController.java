package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.dto.request.LoginReq;
import com.campus.trend.campus_pulse.dto.response.LoginResponse;
import com.campus.trend.campus_pulse.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issues a normal Redis-backed user session to the signed lottery service.
 * InternalServiceFilter authenticates and replay-protects the request first.
 */
@RestController
@RequestMapping("/api/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private static final String LOTTERY_SERVICE_ID = "campus-lottery-station";

    private final AuthService authService;

    @PostMapping("/bot-login")
    public Result<LoginResponse> botLogin(
            @Valid @RequestBody BotLoginRequest request,
            @RequestAttribute(name = "internal.serviceId", required = false) String serviceId) {
        if (!LOTTERY_SERVICE_ID.equals(serviceId)) {
            return Result.error(ResultCode.NO_PERMISSION, "仅抽奖服务可申请机器人会话");
        }

        LoginReq login = new LoginReq();
        login.setLoginType("password");
        login.setAccount(request.getAccount());
        login.setPassword(request.getPassword());
        login.setRememberMe(true);
        return Result.success(authService.login(
                login,
                "zens-lottery-station",
                "internal-service:" + LOTTERY_SERVICE_ID));
    }

    @Data
    public static class BotLoginRequest {
        @NotBlank
        private String account;

        @NotBlank
        private String password;
    }
}
