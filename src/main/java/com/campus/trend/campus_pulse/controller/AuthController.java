package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.LoginReq;
import com.campus.trend.campus_pulse.dto.request.RefreshTokenReq;
import com.campus.trend.campus_pulse.dto.request.RegisterReq;
import com.campus.trend.campus_pulse.dto.request.SendCodeRequest;
import com.campus.trend.campus_pulse.dto.request.GithubLoginReq;
import com.campus.trend.campus_pulse.dto.request.TwoFactorLoginVerifyReq;
import com.campus.trend.campus_pulse.dto.request.TwoFactorToggleReq;
import com.campus.trend.campus_pulse.dto.response.EmailCheckResp;
import com.campus.trend.campus_pulse.dto.response.UsernameCheckResp;
import com.campus.trend.campus_pulse.config.TurnstileProperties;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.TurnstileService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.service.VerificationCodeService;
import com.campus.trend.campus_pulse.utils.ClientIpUtils;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final TurnstileService turnstileService;
    private final TurnstileProperties turnstileProperties;
    private final VerificationCodeService verificationCodeService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService,
            TurnstileService turnstileService,
            TurnstileProperties turnstileProperties,
            VerificationCodeService verificationCodeService,
            UserService userService,
            JwtUtil jwtUtil) {
        this.authService = authService;
        this.turnstileService = turnstileService;
        this.turnstileProperties = turnstileProperties;
        this.verificationCodeService = verificationCodeService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Song：发送邮箱验证码（注册/登录共用）
     */
    @PostMapping("/send-code")
    public Result<?> sendCode(@Valid @RequestBody SendCodeRequest request) {
        log.info("收到发送验证码请求: {}", request.getEmail());
        verificationCodeService.sendCode(request.getEmail());
        return Result.success("验证码已发送，请查收邮件");
    }

    @PostMapping("/verify-code")
    public Result<?> verifyCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        if (email == null || code == null) {
            return Result.failed("邮箱和验证码不能为空");
        }

        boolean valid = verificationCodeService.checkCodeWithoutConsuming(email, code);
        if (!valid) {
            return Result.failed("验证码错误或已失效");
        }

        return Result.success("验证码校验通过");
    }

    /**
     * Song：检查邮箱是否已注册
     */
    @PostMapping("/check-email")
    public Result<EmailCheckResp> checkEmail(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return Result.failed("邮箱不能为空");
        }

        User user = userService.lambdaQuery().eq(User::getEmail, email).one();
        return Result.success(new EmailCheckResp(user != null, user != null ? user.getUsername() : null));
    }

    /**
     * Song：检查用户名是否可用
     */
    @GetMapping("/check-username")
    public Result<UsernameCheckResp> checkUsername(@RequestParam String username) {
        if (username == null || username.isBlank()) {
            return Result.failed("用户名不能为空");
        }

        User user = userService.lambdaQuery().eq(User::getUsername, username).one();
        return Result.success(user == null ? UsernameCheckResp.ok() : UsernameCheckResp.taken("用户名已被使用"));
    }

    /**
     * Song：登录（支持两种方式）
     */
    @PostMapping("/login")
    public Result<?> login(
            @RequestBody LoginReq loginRequest,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        if (!shouldSkipTurnstileForLocalPreview(request, clientIp)) {
            turnstileService.verifyLoginToken(loginRequest.getCfTurnstileResponse(), clientIp);
        }
        return Result.success(authService.login(loginRequest, deviceId, clientIp));
    }

    @PostMapping("/refresh")
    public Result<?> refresh(
            @Valid @RequestBody RefreshTokenReq req,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletRequest request) {
        return Result.success(authService.refresh(req.getRefreshToken(), deviceId, resolveClientIp(request)));
    }

    @GetMapping("/github/authorize-url")
    public Result<?> githubAuthorizeUrl() {
        return Result.success(Map.of("url", authService.buildGithubAuthorizeUrl()));
    }

    @PostMapping("/github/login")
    public Result<?> githubLogin(
            @Valid @RequestBody GithubLoginReq req,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletRequest request) {
        return Result.success(authService.githubLogin(req, deviceId, resolveClientIp(request)));
    }

    @PostMapping("/2fa/verify-login")
    public Result<?> verifyTwoFactorLogin(
            @Valid @RequestBody TwoFactorLoginVerifyReq req,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletRequest request) {
        return Result.success(authService.verifyTwoFactorLogin(req.getTicket(), req.getCode(), deviceId, resolveClientIp(request)));
    }

    @PostMapping("/2fa/setup")
    public Result<?> setupTwoFactor() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        return Result.success(authService.initTwoFactorSetup(userId));
    }

    @PostMapping("/2fa/enable")
    public Result<?> enableTwoFactor(@Valid @RequestBody TwoFactorToggleReq req) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        authService.enableTwoFactor(userId, req.getCode());
        return Result.success("二步验证已开启");
    }

    @PostMapping("/2fa/disable")
    public Result<?> disableTwoFactor(@Valid @RequestBody TwoFactorToggleReq req) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        authService.disableTwoFactor(userId, req.getCode());
        return Result.success("二步验证已关闭");
    }

    /**
     * Song：注册
     */
    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterReq registerRequest) {
        authService.register(registerRequest);
        return Result.success("注册成功");
    }

    /**
     * Song：忘记密码 — 重置密码
     */
    @PostMapping("/reset-password")
    public Result<?> resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        String newPassword = body.get("newPassword");
        authService.resetPassword(email, code, newPassword);
        return Result.success("密码重置成功，请使用新密码登录");
    }

    /**
     * Song：登出
     */
    @PostMapping("/logout")
    public Result<?> logout(HttpServletRequest request) {
        String accessToken = jwtUtil.getToken(request);
        if (accessToken != null && !accessToken.isBlank()) {
            authService.logoutByAccessToken(accessToken);
        } else {
            authService.logout();
        }
        return Result.success();
    }

    private String resolveClientIp(HttpServletRequest request) {
        return ClientIpUtils.resolveClientIp(request);
    }
    /**
     * 本地环境独有：跳过前端cloudflare turnstil 验证
     */
    private boolean shouldSkipTurnstileForLocalPreview(HttpServletRequest request, String clientIp) {
        if (!turnstileProperties.isAllowLocalPreviewSkip()) {
            return false;
        }
        if (!isLoopbackIp(clientIp)) {
            return false;
        }
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        String serverName = request.getServerName();
        boolean localPageRequest = isLocalUrl(origin) || isLocalUrl(referer) || isLocalHost(serverName);
        if (localPageRequest) {
            log.info("检测到本地预览登录请求，跳过 Turnstile 校验: ip={}, origin={}, referer={}", clientIp, origin, referer);
        }
        return localPageRequest;
    }

    private boolean isLoopbackIp(String ip) {
        return "127.0.0.1".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip);
    }

    private boolean isLocalHost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String normalized = host.trim().toLowerCase();
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized);
    }

    private boolean isLocalUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            return isLocalHost(uri.getHost());
        } catch (IllegalArgumentException ex) {
            log.debug("解析本地预览 URL 失败: {}", url, ex);
            return false;
        }
    }
}
