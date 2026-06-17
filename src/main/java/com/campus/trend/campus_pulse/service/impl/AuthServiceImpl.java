package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.dto.request.GithubLoginReq;
import com.campus.trend.campus_pulse.dto.request.LoginReq;
import com.campus.trend.campus_pulse.dto.request.RegisterReq;
import com.campus.trend.campus_pulse.dto.response.LoginResponse;
import com.campus.trend.campus_pulse.dto.response.TwoFactorSetupResp;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.common.exception.LoginException;
import com.campus.trend.campus_pulse.common.exception.RegisterException;
import com.campus.trend.campus_pulse.common.exception.UserAlreadyExistsException;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.InviteCodeService;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.service.VerificationCodeService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import com.campus.trend.campus_pulse.utils.TotpUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String ACCESS_PREFIX = "auth:access:";
    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String DEVICE_PREFIX = "auth:device:";
    private static final String MFA_TICKET_PREFIX = "auth:mfa:ticket:";
    private static final String MFA_SETUP_PREFIX = "auth:mfa:setup:";
    private static final String GITHUB_STATE_PREFIX = "auth:github:state:";
    private static final String LOGIN_SEEN_PREFIX = "auth:login:seen:";
    private static final long LOGIN_CONTEXT_TTL_DAYS = 30;
    private static final long LOGIN_IP_TYPE_TTL_DAYS = 7;

    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final VerificationCodeService verificationCodeService;
    private final LevelService levelService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final InviteCodeService inviteCodeService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${oauth.github.client-id:}")
    private String githubClientId;
    @Value("${oauth.github.client-secret:}")
    private String githubClientSecret;
    @Value("${oauth.github.redirect-uri:}")
    private String githubRedirectUri;
    @Value("${security.two-factor.issuer:Zens社区}")
    private String twoFactorIssuer;
    @Value("${security.two-factor.login-ticket-expire-seconds:300}")
    private long twoFactorTicketExpireSeconds;
    @Value("${security.two-factor.setup-expire-seconds:600}")
    private long twoFactorSetupExpireSeconds;
    @Value("${auth.session.single-device:false}")
    private boolean singleDeviceSessionEnabled;
    @Value("${auth.session.anomaly-notify-cooldown-seconds:3600}")
    private long anomalyNotifyCooldownSeconds;
    @Value("${campus.invite.required:false}")
    private boolean inviteRequired;

    public AuthServiceImpl(PasswordEncoder passwordEncoder, StringRedisTemplate stringRedisTemplate, JwtUtil jwtUtil,
            UserService userService, VerificationCodeService verificationCodeService, LevelService levelService,
            NotificationService notificationService, ObjectMapper objectMapper, InviteCodeService inviteCodeService,
            SimpMessagingTemplate messagingTemplate) {
        this.passwordEncoder = passwordEncoder;
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.verificationCodeService = verificationCodeService;
        this.levelService = levelService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.inviteCodeService = inviteCodeService;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public LoginResponse login(LoginReq req, String deviceId, String clientIp) {
        String loginType = req.getLoginType();
        if (!StringUtils.hasText(loginType)) {
            loginType = StringUtils.hasText(req.getCode()) ? "otp" : "password";
        }
        User user = "otp".equals(loginType) ? loginByOtp(req) : loginByPassword(req);
        return finalizeLogin(user, req.isRememberMe(), normalizeDeviceId(deviceId), null, clientIp, req.getTwoFactorCode());
    }

    @Override
    public LoginResponse refresh(String refreshToken, String deviceId, String clientIp) {
        if (!StringUtils.hasText(refreshToken)) throw new LoginException("refreshToken不能为空");
        if (!jwtUtil.validate(refreshToken)) throw new LoginException("登录已过期，请重新登录");
        if (!"refresh".equalsIgnoreCase(jwtUtil.getClaimString(refreshToken, "typ"))) throw new LoginException("无效的刷新令牌");

        String userId = jwtUtil.getUserId(refreshToken);
        String sessionId = jwtUtil.getClaimString(refreshToken, "sid");
        String tokenDid = normalizeDeviceId(jwtUtil.getClaimString(refreshToken, "did"));
        boolean rememberMe = Boolean.TRUE.equals(jwtUtil.getClaimBoolean(refreshToken, "remember"));
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) throw new LoginException("令牌上下文缺失，请重新登录");
        String reqDid = normalizeDeviceId(deviceId);
        if (StringUtils.hasText(tokenDid) && !tokenDid.equals(reqDid)) throw new LoginException("检测到异常设备，请重新登录");
        String refreshKey = buildRefreshKey(userId, sessionId);
        String redisRefreshHash = stringRedisTemplate.opsForValue().get(refreshKey);
        if (!StringUtils.hasText(redisRefreshHash) || !redisRefreshHash.equals(hashToken(refreshToken))) throw new LoginException("登录状态已失效，请重新登录");
        String redisDid = stringRedisTemplate.opsForValue().get(buildDeviceKey(userId, sessionId));
        if (StringUtils.hasText(redisDid) && !normalizeDeviceId(redisDid).equals(reqDid)) throw new LoginException("检测到异常设备，请重新登录");
        User user = userService.getById(userId);
        ensureUserCanLogin(user);
        return generateTokenResponse(user, rememberMe, reqDid, sessionId, clientIp);
    }

    @Override
    public String buildGithubAuthorizeUrl() {
        ensureGithubConfigReady();
        String state = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(GITHUB_STATE_PREFIX + state, "1", 10, TimeUnit.MINUTES);
        return UriComponentsBuilder.fromHttpUrl("https://github.com/login/oauth/authorize")
                .queryParam("client_id", githubClientId)
                .queryParam("redirect_uri", githubRedirectUri)
                .queryParam("scope", "read:user user:email")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public LoginResponse githubLogin(GithubLoginReq req, String deviceId, String clientIp) {
        ensureGithubConfigReady();
        validateGithubState(req.getState());
        String ghToken = fetchGithubAccessToken(req.getCode(), req.getState());
        Map<String, Object> ghUser = fetchGithubUser(ghToken);
        String githubId = toSafeString(ghUser.get("id"));
        String githubLogin = toSafeString(ghUser.get("login"));
        String avatar = toSafeString(ghUser.get("avatar_url"));
        String email = toSafeString(ghUser.get("email"));
        if (!StringUtils.hasText(githubId)) throw new LoginException("GitHub账号信息无效");
        if (!StringUtils.hasText(email)) email = fetchGithubPrimaryEmail(ghToken);
        User user = userService.lambdaQuery().eq(User::getGithubId, githubId).one();
        if (user == null && StringUtils.hasText(email)) {
            user = userService.lambdaQuery().eq(User::getEmail, email).one();
            if (user != null) {
                user.setGithubId(githubId);
                user.setGithubLogin(githubLogin);
                if (StringUtils.hasText(avatar) && !StringUtils.hasText(user.getAvatar())) user.setAvatar(avatar);
                userService.updateById(user);
            }
        }
        if (user == null) user = createUserByGithub(githubId, githubLogin, avatar, email);
        ensureUserCanLogin(user);
        return finalizeLogin(user, req.isRememberMe(), normalizeDeviceId(deviceId), null, clientIp, req.getTwoFactorCode());
    }

    @Override
    public LoginResponse verifyTwoFactorLogin(String ticket, String code, String deviceId, String clientIp) {
        if (!StringUtils.hasText(ticket) || !StringUtils.hasText(code)) throw new LoginException("二步验证参数不能为空");
        LoginContext ctx = readLoginContext(ticket);
        if (ctx == null || !StringUtils.hasText(ctx.userId)) throw new LoginException("二步验证已过期，请重新登录");
        User user = userService.getById(ctx.userId);
        ensureUserCanLogin(user);
        if (!(user.getTwoFactorEnabled() != null && user.getTwoFactorEnabled() == 1) || !StringUtils.hasText(user.getTwoFactorSecret())) {
            throw new LoginException("用户未开启二步验证");
        }
        if (!TotpUtil.verifyCode(user.getTwoFactorSecret(), code)) throw new LoginException("二步验证码错误");
        String reqDid = normalizeDeviceId(deviceId);
        String ticketDid = normalizeDeviceId(ctx.deviceId);
        if (StringUtils.hasText(ticketDid) && !ticketDid.equals(reqDid)) throw new LoginException("检测到设备切换，请重新登录");
        stringRedisTemplate.delete(MFA_TICKET_PREFIX + ticket);
        String finalIp = StringUtils.hasText(ctx.clientIp) ? ctx.clientIp : clientIp;
        return generateTokenResponse(user, ctx.rememberMe, ticketDid, null, finalIp);
    }

    @Override
    public TwoFactorSetupResp initTwoFactorSetup(String userId) {
        User user = userService.getById(userId);
        ensureUserCanLogin(user);
        String secret = TotpUtil.generateSecret();
        stringRedisTemplate.opsForValue().set(MFA_SETUP_PREFIX + userId, secret, twoFactorSetupExpireSeconds, TimeUnit.SECONDS);
        String account = StringUtils.hasText(user.getEmail()) ? user.getEmail() : user.getUsername();
        String otpauthUri = TotpUtil.buildOtpAuthUri(twoFactorIssuer, account, secret);
        String qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=220x220&data="
                + UriComponentsBuilder.newInstance().queryParam("v", otpauthUri).build().getQueryParams().getFirst("v");

        TwoFactorSetupResp resp = new TwoFactorSetupResp();
        resp.setSecret(secret);
        resp.setOtpauthUri(otpauthUri);
        resp.setQrCodeUrl(qrCodeUrl);
        resp.setExpireSeconds((int) twoFactorSetupExpireSeconds);
        return resp;
    }

    @Override
    public void enableTwoFactor(String userId, String code) {
        User user = userService.getById(userId);
        ensureUserCanLogin(user);
        String secret = stringRedisTemplate.opsForValue().get(MFA_SETUP_PREFIX + userId);
        if (!StringUtils.hasText(secret)) throw new LoginException("二步验证配置已过期，请重新生成二维码");
        if (!TotpUtil.verifyCode(secret, code)) throw new LoginException("二步验证码错误，请检查后重试");
        user.setTwoFactorEnabled(1);
        user.setTwoFactorSecret(secret);
        userService.updateById(user);
        stringRedisTemplate.delete(MFA_SETUP_PREFIX + userId);
        try {
            notificationService.sendTwoFactorToggleNotification(userId, true);
        } catch (Exception ex) {
            log.warn("2FA 开启通知发送失败: userId={}, err={}", userId, ex.getMessage());
        }
    }

    @Override
    public void disableTwoFactor(String userId, String code) {
        User user = userService.getById(userId);
        ensureUserCanLogin(user);
        if (!(user.getTwoFactorEnabled() != null && user.getTwoFactorEnabled() == 1) || !StringUtils.hasText(user.getTwoFactorSecret())) {
            return;
        }
        if (!TotpUtil.verifyCode(user.getTwoFactorSecret(), code)) throw new LoginException("二步验证码错误，无法关闭");
        user.setTwoFactorEnabled(0);
        user.setTwoFactorSecret(null);
        userService.updateById(user);
        stringRedisTemplate.delete(MFA_SETUP_PREFIX + userId);
        try {
            notificationService.sendTwoFactorToggleNotification(userId, false);
        } catch (Exception ex) {
            log.warn("2FA 关闭通知发送失败: userId={}, err={}", userId, ex.getMessage());
        }
    }

    private User loginByOtp(LoginReq req) {
        String email = req.getEmail();
        String code = req.getCode();
        if (!StringUtils.hasText(email)) throw new LoginException("登录邮箱不能为空");
        if (!StringUtils.hasText(code)) throw new LoginException("请输入验证码");
        User user = userService.lambdaQuery().eq(User::getEmail, email).one();
        if (user == null) throw new LoginException("该邮箱尚未注册，请先注册");
        if (!verificationCodeService.verifyCode(email, code)) throw new LoginException("验证码错误或已失效");
        return user;
    }

    private User loginByPassword(LoginReq req) {
        String account = req.getAccount();
        String password = req.getPassword();
        if (!StringUtils.hasText(account)) throw new LoginException("请输入用户名或邮箱");
        if (!StringUtils.hasText(password)) throw new LoginException("请输入密码");
        User user = account.contains("@")
                ? userService.lambdaQuery().eq(User::getEmail, account).one()
                : userService.lambdaQuery().eq(User::getUsername, account).one();
        if (user == null) throw new LoginException("账号不存在，请检查后重试");
        if (!passwordEncoder.matches(password, user.getPassword())) {
            trackLoginFailure(user.getId());
            throw new LoginException("密码错误");
        }
        return user;
    }

    private static final int LOGIN_FAILURE_BURST_THRESHOLD = 5;
    private static final long LOGIN_FAILURE_WINDOW_SECONDS = 600;

    /** 记录登录失败次数，达到阈值后发送安全通知（10 分钟滑窗，每个阈值只通知一次）。 */
    private void trackLoginFailure(String userId) {
        if (!StringUtils.hasText(userId)) return;
        try {
            String key = "auth:login:fail:" + userId;
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, LOGIN_FAILURE_WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            if (count != null && count == LOGIN_FAILURE_BURST_THRESHOLD) {
                notificationService.sendLoginFailedBurstNotification(userId, count.intValue(), null);
            }
        } catch (Exception e) {
            log.warn("登录失败计数失败: userId={}, err={}", userId, e.getMessage());
        }
    }

    private LoginResponse finalizeLogin(User user, boolean rememberMe, String deviceId, String existsSessionId, String clientIp, String twoFactorCode) {
        ensureUserCanLogin(user);
        if (isTwoFactorEnabled(user)) {
            if (StringUtils.hasText(twoFactorCode)) {
                if (!TotpUtil.verifyCode(user.getTwoFactorSecret(), twoFactorCode)) throw new LoginException("二步验证码错误");
            } else {
                return buildTwoFactorChallenge(user, rememberMe, deviceId, clientIp);
            }
        }
        return generateTokenResponse(user, rememberMe, deviceId, existsSessionId, clientIp);
    }

    private LoginResponse generateTokenResponse(User user, boolean rememberMe, String deviceId, String existsSessionId, String clientIp) {
        String role = user.getRole() != null ? user.getRole() : "ROLE_USER";
        // Song：管理员登录时自动同步 trust_level = 4（领袖），保证前后端展示与权限判断一致
        if ("ROLE_ADMIN".equals(role) || "ROLE_SUPER_ADMIN".equals(role)) {
            if (user.getTrustLevel() == null || user.getTrustLevel() < 4) {
                user.setTrustLevel(4);
                try {
                    userService.updateById(new com.campus.trend.campus_pulse.entity.User().setId(user.getId()).setTrustLevel(4));
                } catch (Exception ignored) {
                    // 同步失败不阻断登录，getTrustLevel() 已有管理员兜底
                }
            }
        }
        List<String> roleCodes = List.of(role);
        String sessionId = StringUtils.hasText(existsSessionId) ? existsSessionId : UUID.randomUUID().toString();
        String safeDid = normalizeDeviceId(deviceId);
        applySessionPolicyBeforeIssueToken(user.getId(), safeDid, clientIp);
        Map<String, Object> baseClaims = jwtUtil.buildClaims(user.getUsername(), roleCodes, user.getAvatar());
        baseClaims.put("sid", sessionId);
        baseClaims.put("did", safeDid);
        baseClaims.put("remember", rememberMe);
        Map<String, Object> accessClaims = new HashMap<>(baseClaims);
        accessClaims.put("typ", "access");
        Map<String, Object> refreshClaims = new HashMap<>(baseClaims);
        refreshClaims.put("typ", "refresh");
        String accessToken = jwtUtil.generateAccessToken(user.getId(), accessClaims, rememberMe);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), refreshClaims, rememberMe);
        long accessTtlMs = jwtUtil.resolveAccessExpireMs(rememberMe);
        long refreshTtlMs = jwtUtil.resolveRefreshExpireMs(rememberMe);
        stringRedisTemplate.opsForValue().set(buildAccessKey(user.getId(), sessionId), accessToken, accessTtlMs, TimeUnit.MILLISECONDS);
        stringRedisTemplate.opsForValue().set(buildRefreshKey(user.getId(), sessionId), hashToken(refreshToken), refreshTtlMs, TimeUnit.MILLISECONDS);
        stringRedisTemplate.opsForValue().set(buildDeviceKey(user.getId(), sessionId), safeDid, refreshTtlMs, TimeUnit.MILLISECONDS);
        rememberLoginContext(user.getId(), safeDid, clientIp);
        stringRedisTemplate.opsForValue().set("access_token" + user.getId(), accessToken, accessTtlMs, TimeUnit.MILLISECONDS);
        stringRedisTemplate.opsForValue().set("refresh_token" + user.getId(), refreshToken, refreshTtlMs, TimeUnit.MILLISECONDS);
        userService.updateLastActiveTime(user.getId());
        String activeRegion = resolveActiveRegion(clientIp);
        if (StringUtils.hasText(activeRegion)) userService.updateActiveRegion(user.getId(), activeRegion);
        try {
            String loginExpKey = "exp:login:" + user.getId() + ":" + LocalDate.now();
            Boolean alreadyGranted = stringRedisTemplate.hasKey(loginExpKey);
            if (!Boolean.TRUE.equals(alreadyGranted)) {
                levelService.addExperience(user.getId(), 1, "每日登录");
                stringRedisTemplate.opsForValue().set(loginExpKey, "1", 1, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.warn("每日登录经验发放失败: {}", e.getMessage());
        }
        // 异步：登录成功通知（不阻塞登录响应）
        try {
            String deviceType = resolveDeviceType(safeDid).equals("mobile") ? "移动端" : "PC端";
            String region = StringUtils.hasText(activeRegion) ? activeRegion : (StringUtils.hasText(clientIp) ? clientIp : "未知位置");
            String loginTime = java.time.format.DateTimeFormatter.ofPattern("MM月dd日 HH:mm")
                    .format(java.time.LocalDateTime.now());
            String notifyKey = "auth:login:notify:" + user.getId() + ":" + LocalDate.now() + ":" + safeDid.hashCode();
            Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(notifyKey, "1", 24, TimeUnit.HOURS);
            if (Boolean.TRUE.equals(first)) {
                notificationService.createNotification(
                        user.getId(),
                        "system",
                        "账号登录通知",
                        "你的账号于 " + loginTime + " 在" + deviceType + "登录，登录地点：" + region + "。如非本人操作，请立即修改密码。",
                        null,
                        null);
            }
        } catch (Exception e) {
            log.warn("登录通知发送失败: {}", e.getMessage());
        }
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTwoFactorRequired(false);
        return response;
    }

    /**
     * 登录前执行会话策略：
     * - singleDeviceSessionEnabled=true: 同一设备类型(PC/Mobile)只允许一个会话，踢旧会话
     * - 否则: 仅检测异常登录并通知
     */
    private void applySessionPolicyBeforeIssueToken(String userId, String deviceId, String clientIp) {
        if (!StringUtils.hasText(userId)) return;

        if (singleDeviceSessionEnabled) {
            String deviceType = resolveDeviceType(deviceId);
            kickSessionsByDeviceType(userId, deviceType, deviceId);
            return;
        }

        detectAndNotifyAnomalyLogin(userId, deviceId, clientIp);
    }

    /**
     * 根据 deviceId 判断设备类型（简单按前缀区分，可扩展）
     * 约定: deviceId 以 "mob-" 开头为移动端，其余为PC端
     */
    private String resolveDeviceType(String deviceId) {
        if (!StringUtils.hasText(deviceId)) return "pc";
        return deviceId.startsWith("mob-") ? "mobile" : "pc";
    }

    /**
     * 踢掉同设备类型的旧会话（保留不同设备类型的会话）
     */
    private void kickSessionsByDeviceType(String userId, String deviceType, String currentDeviceId) {
        try {
            List<String> deviceKeys = scanKeys(DEVICE_PREFIX + userId + ":*");
            if (deviceKeys == null || deviceKeys.isEmpty()) return;
            boolean kicked = false;
            for (String key : deviceKeys) {
                String oldDevice = normalizeDeviceId(stringRedisTemplate.opsForValue().get(key));
                if (!StringUtils.hasText(oldDevice) || oldDevice.equals(currentDeviceId)) continue;
                // 同设备类型才踢
                if (deviceType.equals(resolveDeviceType(oldDevice))) {
                    String prefix = DEVICE_PREFIX + userId + ":";
                    String sessionId = key.substring(prefix.length());
                    clearSession(userId, sessionId);
                    kicked = true;
                    log.info("踢下线旧会话: userId={}, deviceType={}, sessionId={}", userId, deviceType, sessionId);
                }
            }
            // 发送 WS 强制下线通知 + 站内信留底
            if (kicked) {
                try {
                    messagingTemplate.convertAndSendToUser(userId, "/queue/force-logout",
                            java.util.Map.of("reason", "another_device_login"));
                } catch (Exception ex) {
                    log.warn("WS 强制下线推送失败: userId={}, err={}", userId, ex.getMessage());
                }
                try {
                    notificationService.sendSessionTerminatedNotification(userId, deviceType, null);
                } catch (Exception ex) {
                    log.warn("强制下线站内信发送失败: userId={}, err={}", userId, ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("踢下线旧会话失败: userId={}, err={}", userId, e.getMessage());
        }
    }

    private void detectAndNotifyAnomalyLogin(String userId, String deviceId, String clientIp) {
        if (!StringUtils.hasText(deviceId) || "unknown-device".equals(deviceId)) {
            return;
        }
        try {
            List<String> keys = scanKeys(DEVICE_PREFIX + userId + ":*");
            if (keys == null || keys.isEmpty()) {
                return;
            }

            boolean detected = false;
            for (String key : keys) {
                String oldDevice = normalizeDeviceId(stringRedisTemplate.opsForValue().get(key));
                if (StringUtils.hasText(oldDevice) && !oldDevice.equals(deviceId)) {
                    detected = true;
                    break;
                }
            }
            if (!detected) {
                return;
            }

            String deviceType = resolveDeviceType(deviceId);
            if (isKnownLoginContext(userId, deviceId, clientIp, deviceType)) {
                return;
            }

            long cooldown = Math.max(60L, anomalyNotifyCooldownSeconds);
            String throttleKey = "auth:anomaly:notice:" + userId + ":" + deviceType + ":" + shortHash(clientIp);
            Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(throttleKey, "1", cooldown, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(first)) {
                return;
            }

            notificationService.sendNewDeviceLoginNotification(userId, deviceType, clientIp, null);
            log.warn("检测到异常登录行为: userId={}, deviceId={}, ip={}", userId, deviceId,
                    StringUtils.hasText(clientIp) ? clientIp : "未知IP");
        } catch (Exception e) {
            log.warn("异常登录检测失败: userId={}, err={}", userId, e.getMessage());
        }
    }

    private void rememberLoginContext(String userId, String deviceId, String clientIp) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(deviceId) || "unknown-device".equals(deviceId)) {
            return;
        }
        try {
            String deviceType = resolveDeviceType(deviceId);
            String ipKey = normalizeLoginIp(clientIp);
            stringRedisTemplate.opsForValue().set(loginSeenDeviceKey(userId, deviceId, ipKey), "1", LOGIN_CONTEXT_TTL_DAYS, TimeUnit.DAYS);
            if (StringUtils.hasText(ipKey)) {
                stringRedisTemplate.opsForValue().set(loginSeenIpTypeKey(userId, deviceType, ipKey), "1", LOGIN_IP_TYPE_TTL_DAYS, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.debug("记录登录上下文失败: userId={}, err={}", userId, e.getMessage());
        }
    }

    private boolean isKnownLoginContext(String userId, String deviceId, String clientIp, String deviceType) {
        String ipKey = normalizeLoginIp(clientIp);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(loginSeenDeviceKey(userId, deviceId, ipKey)))) {
                return true;
            }
            return StringUtils.hasText(ipKey)
                    && Boolean.TRUE.equals(stringRedisTemplate.hasKey(loginSeenIpTypeKey(userId, deviceType, ipKey)));
        } catch (Exception e) {
            log.debug("读取登录上下文失败: userId={}, err={}", userId, e.getMessage());
            return false;
        }
    }

    private String loginSeenDeviceKey(String userId, String deviceId, String ipKey) {
        return LOGIN_SEEN_PREFIX + "device:" + userId + ":" + shortHash(deviceId) + ":" + shortHash(ipKey);
    }

    private String loginSeenIpTypeKey(String userId, String deviceType, String ipKey) {
        return LOGIN_SEEN_PREFIX + "iptype:" + userId + ":" + deviceType + ":" + shortHash(ipKey);
    }

    private String normalizeLoginIp(String clientIp) {
        if (!StringUtils.hasText(clientIp)) return null;
        String normalized = clientIp.trim().toLowerCase();
        if (!StringUtils.hasText(normalized) || "unknown".equals(normalized)) return null;
        return normalized;
    }

    private String shortHash(String value) {
        String source = StringUtils.hasText(value) ? value : "-";
        return hashToken(source).substring(0, 16);
    }

    private LoginResponse buildTwoFactorChallenge(User user, boolean rememberMe, String deviceId, String clientIp) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        LoginContext ctx = new LoginContext();
        ctx.userId = user.getId();
        ctx.rememberMe = rememberMe;
        ctx.deviceId = normalizeDeviceId(deviceId);
        ctx.clientIp = clientIp;
        try {
            String json = objectMapper.writeValueAsString(ctx);
            stringRedisTemplate.opsForValue().set(MFA_TICKET_PREFIX + ticket, json, twoFactorTicketExpireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new LoginException("创建二步验证票据失败");
        }
        LoginResponse response = new LoginResponse();
        response.setTwoFactorRequired(true);
        response.setTwoFactorTicket(ticket);
        return response;
    }

    private LoginContext readLoginContext(String ticket) {
        try {
            String json = stringRedisTemplate.opsForValue().get(MFA_TICKET_PREFIX + ticket);
            if (!StringUtils.hasText(json)) return null;
            return objectMapper.readValue(json, LoginContext.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterReq req) {
        // 邀请码校验（配置开启时必填）
        if (inviteRequired) {
            if (!org.springframework.util.StringUtils.hasText(req.getInviteCode())) {
                throw new RegisterException("注册需要邀请码");
            }
            if (!inviteCodeService.validate(req.getInviteCode())) {
                throw new RegisterException("邀请码无效或已过期");
            }
        } else if (org.springframework.util.StringUtils.hasText(req.getInviteCode())) {
            // 非必填但填了，也校验有效性
            if (!inviteCodeService.validate(req.getInviteCode())) {
                throw new RegisterException("邀请码无效或已过期");
            }
        }
        if (!verificationCodeService.verifyCode(req.getEmail(), req.getCode())) throw new RegisterException("验证码错误或已失效");
        User exist = userService.lambdaQuery().eq(User::getUsername, req.getUsername()).one();
        if (exist != null) throw new UserAlreadyExistsException("该用户名已被注册");
        User existEmail = userService.lambdaQuery().eq(User::getEmail, req.getEmail()).one();
        if (existEmail != null) throw new RegisterException("该邮箱已被其它账号注册");

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setAvatar(req.getAvatar());
        user.setEmail(req.getEmail());
        user.setMajor(req.getMajor());
        user.setEnrollmentYear(req.getGrade());
        user.setGender(req.getGender());
        user.setSchool(req.getSchool());
        user.setStatus(1);
        user.setRole("ROLE_USER");
        user.setReputation(100);
        user.setContributionVal(0);
        user.setTotalPosts(0);
        user.setTotalLikesReceived(0);
        user.setTwoFactorEnabled(0);
        user.setEmailNotifyEnabled(1);
        boolean saved = userService.save(user);
        if (!saved) throw new RegisterException("注册失败，请稍后重试");
        // 消耗邀请码
        if (org.springframework.util.StringUtils.hasText(req.getInviteCode())) {
            try { inviteCodeService.consume(req.getInviteCode(), user.getId()); } catch (Exception e) {
                log.warn("邀请码消耗失败（不影响注册）: code={}, err={}", req.getInviteCode(), e.getMessage());
            }
        }
    }

    @Override
    public void logout() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();
        clearAllSessionsByUserId(userId);
        SecurityContextHolder.clearContext();
    }

    @Override
    public void logoutByAccessToken(String accessToken) {
        String userId = jwtUtil.getUserId(accessToken);
        String sessionId = jwtUtil.getClaimString(accessToken, "sid");
        if (!StringUtils.hasText(userId)) {
            SecurityContextHolder.clearContext();
            return;
        }
        if (StringUtils.hasText(sessionId)) clearSession(userId, sessionId);
        stringRedisTemplate.delete("access_token" + userId);
        stringRedisTemplate.delete("refresh_token" + userId);
        SecurityContextHolder.clearContext();
    }

    @Override
    public void resetPassword(String email, String code, String newPassword) {
        if (!StringUtils.hasText(email)) throw new LoginException("邮箱不能为空");
        if (!StringUtils.hasText(code)) throw new LoginException("验证码不能为空");
        if (newPassword == null || newPassword.length() < 6) throw new LoginException("新密码长度不能少于6位");
        if (!verificationCodeService.verifyCode(email, code)) throw new LoginException("验证码错误或已失效");
        User user = userService.lambdaQuery().eq(User::getEmail, email).one();
        if (user == null) throw new LoginException("该邮箱未注册");
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateById(user);
        clearAllSessionsByUserId(user.getId());
        try {
            notificationService.sendPasswordResetNotification(user.getId(), null);
        } catch (Exception ex) {
            log.warn("密码重置通知发送失败: userId={}, err={}", user.getId(), ex.getMessage());
        }
    }

    private void ensureUserCanLogin(User user) {
        if (user == null) throw new LoginException("账号不存在，请检查后重试");
        if (user.getStatus() != null && user.getStatus() != 1) throw new LoginException("用户状态异常，请联系管理员");
    }

    private boolean isTwoFactorEnabled(User user) {
        return user != null && user.getTwoFactorEnabled() != null && user.getTwoFactorEnabled() == 1
                && StringUtils.hasText(user.getTwoFactorSecret());
    }

    private String normalizeDeviceId(String deviceId) {
        if (!StringUtils.hasText(deviceId)) return "unknown-device";
        String normalized = deviceId.trim();
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private String resolveActiveRegion(String clientIp) {
        if (!StringUtils.hasText(clientIp)) return null;
        String normalized = clientIp.trim();
        if (!StringUtils.hasText(normalized) || "unknown".equalsIgnoreCase(normalized)) return null;
        if ("127.0.0.1".equals(normalized) || "::1".equals(normalized) || "0:0:0:0:0:0:0:1".equals(normalized)) return "本地开发环境";
        return normalized;
    }

    private String buildAccessKey(String userId, String sessionId) {
        return ACCESS_PREFIX + userId + ":" + sessionId;
    }

    private String buildRefreshKey(String userId, String sessionId) {
        return REFRESH_PREFIX + userId + ":" + sessionId;
    }

    private String buildDeviceKey(String userId, String sessionId) {
        return DEVICE_PREFIX + userId + ":" + sessionId;
    }

    private void clearSession(String userId, String sessionId) {
        stringRedisTemplate.delete(buildAccessKey(userId, sessionId));
        stringRedisTemplate.delete(buildRefreshKey(userId, sessionId));
        stringRedisTemplate.delete(buildDeviceKey(userId, sessionId));
    }

    private void clearAllSessionsByUserId(String userId) {
        if (!StringUtils.hasText(userId)) return;
        clearByPattern(ACCESS_PREFIX + userId + ":*");
        clearByPattern(REFRESH_PREFIX + userId + ":*");
        clearByPattern(DEVICE_PREFIX + userId + ":*");
        stringRedisTemplate.delete("access_token" + userId);
        stringRedisTemplate.delete("refresh_token" + userId);
    }

    private void clearByPattern(String keyPattern) {
        try {
            // 这里别直接 KEYS，用户会话多起来以后它会把 Redis 单线程卡住。
            List<String> keys = scanKeys(keyPattern);
            if (keys != null && !keys.isEmpty()) stringRedisTemplate.delete(keys);
        } catch (Exception e) {
            log.warn("清理会话缓存失败: pattern={}, err={}", keyPattern, e.getMessage());
        }
    }

    private List<String> scanKeys(String keyPattern) {
        return stringRedisTemplate.execute((RedisCallback<List<String>>) connection -> {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(keyPattern)
                    .count(200)
                    .build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    byte[] rawKey = cursor.next();
                    if (rawKey != null && rawKey.length > 0) {
                        keys.add(new String(rawKey, StandardCharsets.UTF_8));
                    }
                }
            }
            return keys;
        });
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new LoginException("令牌摘要失败");
        }
    }

    private void ensureGithubConfigReady() {
        if (!StringUtils.hasText(githubClientId) || !StringUtils.hasText(githubClientSecret) || !StringUtils.hasText(githubRedirectUri)) {
            throw new LoginException("GitHub登录配置不完整，请联系管理员");
        }
    }

    private void validateGithubState(String state) {
        if (!StringUtils.hasText(state)) throw new LoginException("GitHub状态参数缺失");
        String key = GITHUB_STATE_PREFIX + state;
        String val = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(val)) throw new LoginException("GitHub登录状态已失效，请重试");
        stringRedisTemplate.delete(key);
    }

    private String fetchGithubAccessToken(String code, String state) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", githubClientId);
            form.add("client_secret", githubClientSecret);
            form.add("code", code);
            form.add("state", state);
            form.add("redirect_uri", githubRedirectUri);
            ResponseEntity<Map> response = restTemplate.exchange("https://github.com/login/oauth/access_token",
                    HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) throw new LoginException("GitHub令牌交换失败");
            String token = toSafeString(response.getBody().get("access_token"));
            if (!StringUtils.hasText(token)) throw new LoginException("GitHub令牌无效，请重试");
            return token;
        } catch (LoginException e) {
            throw e;
        } catch (Exception e) {
            log.error("GitHub令牌交换异常", e);
            throw new LoginException("GitHub登录失败，请稍后重试");
        }
    }

    private Map<String, Object> fetchGithubUser(String githubAccessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubAccessToken);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set("User-Agent", "CampusPulse");
            ResponseEntity<Map> response = restTemplate.exchange("https://api.github.com/user", HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) throw new LoginException("无法获取GitHub用户信息");
            return response.getBody();
        } catch (LoginException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取GitHub用户信息失败", e);
            throw new LoginException("GitHub登录失败，请稍后重试");
        }
    }

    private String fetchGithubPrimaryEmail(String githubAccessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubAccessToken);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set("User-Agent", "CampusPulse");
            ResponseEntity<String> response = restTemplate.exchange("https://api.github.com/user/emails",
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) return null;
            List<Map<String, Object>> emails = objectMapper.readValue(response.getBody(), new TypeReference<>() {});
            for (Map<String, Object> email : emails) {
                if (Boolean.TRUE.equals(email.get("primary")) && Boolean.TRUE.equals(email.get("verified"))) {
                    return toSafeString(email.get("email"));
                }
            }
            for (Map<String, Object> email : emails) {
                if (Boolean.TRUE.equals(email.get("verified"))) return toSafeString(email.get("email"));
            }
            return null;
        } catch (Exception e) {
            log.warn("获取GitHub邮箱失败: {}", e.getMessage());
            return null;
        }
    }

    private User createUserByGithub(String githubId, String githubLogin, String avatar, String email) {
        User user = new User();
        user.setUsername(generateUniqueGithubUsername(githubLogin, githubId));
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setNickname(StringUtils.hasText(githubLogin) ? githubLogin : user.getUsername());
        user.setAvatar(avatar);
        user.setEmail(email);
        user.setStatus(1);
        user.setRole("ROLE_USER");
        user.setReputation(100);
        user.setContributionVal(0);
        user.setTotalPosts(0);
        user.setTotalLikesReceived(0);
        user.setGithubId(githubId);
        user.setGithubLogin(githubLogin);
        user.setTwoFactorEnabled(0);
        user.setEmailNotifyEnabled(1);
        boolean saved = userService.save(user);
        if (!saved) throw new LoginException("GitHub账号登录失败，请稍后重试");
        return user;
    }

    private String generateUniqueGithubUsername(String githubLogin, String githubId) {
        String base = StringUtils.hasText(githubLogin) ? githubLogin : "gh_" + githubId;
        base = base.replaceAll("[^a-zA-Z0-9_]", "_");
        if (!StringUtils.hasText(base)) base = "gh_user";
        String candidate = base;
        int idx = 1;
        while (userService.lambdaQuery().eq(User::getUsername, candidate).count() > 0) {
            candidate = base + "_" + idx;
            idx++;
            if (idx > 9999) {
                candidate = "gh_" + UUID.randomUUID().toString().substring(0, 8);
                break;
            }
        }
        return candidate.length() > 50 ? candidate.substring(0, 50) : candidate;
    }

    private String toSafeString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static class LoginContext {
        public String userId;
        public boolean rememberMe;
        public String deviceId;
        public String clientIp;
    }
}
