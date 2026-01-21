package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.dto.request.LoginReq;
import com.campus.trend.campus_pulse.dto.request.RegisterReq;
import com.campus.trend.campus_pulse.dto.response.LoginResponse;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.exception.custom.LoginException;
import com.campus.trend.campus_pulse.exception.custom.RedisOperationException;
import com.campus.trend.campus_pulse.exception.custom.RegisterException;
import com.campus.trend.campus_pulse.exception.custom.UserAlreadyExistsException;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.UserProfileService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authorizationManager;

    private final PasswordEncoder passwordEncoder;

    private final StringRedisTemplate stringRedisTemplate;

    private final JwtUtil jwtUtil;

    private final UserService userService;

    private final UserProfileService userProfileService;

    private final com.campus.trend.campus_pulse.service.VerificationCodeService verificationCodeService;

    public AuthServiceImpl(AuthenticationManager authorizationManager,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate stringRedisTemplate,
            JwtUtil jwtUtil, UserService userService,
            UserProfileService userProfileService,
            com.campus.trend.campus_pulse.service.VerificationCodeService verificationCodeService) {
        this.authorizationManager = authorizationManager;
        this.passwordEncoder = passwordEncoder;
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.userProfileService = userProfileService;
        this.verificationCodeService = verificationCodeService;
    }

    @Override
    public LoginResponse login(LoginReq req) {

        // 0.1 检查账号是否被锁定
        if (verificationCodeService.isLocked(req.getUsername())) {
            long timeLeft = verificationCodeService.getLockTimeLeft(req.getUsername());
            throw new LoginException("账号已被锁定，请 " + (timeLeft / 60 + 1) + " 分钟后再试");
        }

        // 0.2 强制校验图形验证码
        if (!verificationCodeService.verifyCaptcha(req.getUuid(), req.getCode())) {
            throw new LoginException("图形验证码错误或已失效");
        }

        // 0. 检查是否需要验证码
        if (verificationCodeService.needVerificationCode(req.getUsername())) {
            if (req.getCode() == null || req.getCode().trim().isEmpty()) {
                throw new LoginException("登录失败次数过多，请输入验证码");
            }
            // 校验验证码
            if (!verificationCodeService.verifyLoginCode(req.getUsername(), req.getCode())) {
                throw new LoginException("验证码错误或已失效");
            }
        }

        // 1. 构?Token（账户密码封装）
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(req.getUsername(),
                req.getPassword());

        // 2. SecurityManager 进行实际认证
        Authentication authentication;
        try {
            authentication = authorizationManager.authenticate(authToken);
        } catch (Exception e) {
            // 登录失败，记录失败次数
            int failCount = verificationCodeService.recordLoginFailure(req.getUsername());
            if (failCount >= 3) {
                long timeLeft = verificationCodeService.getLockTimeLeft(req.getUsername());
                throw new LoginException("登录失败次数过多，账号已锁定，请 " + (timeLeft / 60 + 1) + " 分钟后再试");
            }
            throw new LoginException("登录失败，账号或密码错误（剩余尝试次数：" + (3 - failCount) + "）");
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            int failCount = verificationCodeService.recordLoginFailure(req.getUsername());
            if (failCount >= 3) {
                long timeLeft = verificationCodeService.getLockTimeLeft(req.getUsername());
                throw new LoginException("登录失败次数过多，账号已锁定，请 " + (timeLeft / 60 + 1) + " 分钟后再试");
            }
            throw new LoginException("登录失败，账号或密码错误（剩余尝试次数：" + (3 - failCount) + "）");
        }

        // 3. 获取登录用户信息
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        User user = authUser.getUser();

        // 登录成功，清除失败记录
        verificationCodeService.clearLoginFailure(req.getUsername());

        // 4. JWT 内容,构造自定义 JWT
        Map<String, Object> claims = jwtUtil.buildClaims(user.getUsername(), user.getRole(), user.getAvatar());

        // 5. 生成 Token ,并存入Redis
        String AccessToken = jwtUtil.generateAccessToken(user.getId(), claims, req.isRememberMe());
        String RefreshToken = jwtUtil.generateRefreshToken(user.getId(), claims);

        LoginResponse response = new LoginResponse();
        response.setAccessToken(AccessToken);
        response.setRefreshToken(RefreshToken);

        // 5.1 构建 Redis ，通过Key-用户ID，Value-token
        stringRedisTemplate.opsForValue().set("access_token" + user.getId(), AccessToken);
        stringRedisTemplate.opsForValue().set("refresh_token" + user.getId(), RefreshToken);

        userService.checkAndUpgradeGrade(user);

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterReq req) {

        // 0. 校验验证码
        if (!verificationCodeService.verifyCode(req.getEmail(), req.getCode())) {
            throw new RegisterException("验证码错误或已失效");
        }

        // 1. 用户名（学号）是否已存在
        User exist = userService.lambdaQuery()
                .eq(User::getUsername, req.getUsername())
                .one();

        if (exist != null) {
            throw new UserAlreadyExistsException("该学号已注册");
        }

        // 1.1 校验邮箱是否存在
        User existEmail = userService.lambdaQuery()
                .eq(User::getEmail, req.getEmail())
                .one();
        if (existEmail != null) {
            throw new RegisterException("该邮箱已被其它账号注册");
        }

        // 2. 创建用户实体
        User user = new User();

        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setAvatar(req.getAvatar());
        user.setEmail(req.getEmail());
        user.setMajor(req.getMajor());
        user.setGrade(req.getGrade());
        user.setGender(req.getGender());
        user.setSchool(req.getSchool());
        user.setRole(req.getRole());
        user.setStatus(1); // 默认正常

        // 3. 保存用户
        boolean saved = userService.save(user);
        if (!saved) {
            throw new RegisterException("注册失败，请稍后重试");
        }

        // 4. 同步创建用户画像
        userProfileService.createProfile(user.getId());
    }

    @Override
    public void logout() {
        // 1. 获取当前登录用户
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        // 2. 删除 Redis Token
        if (!stringRedisTemplate.delete("access_token" + userId)
                || !stringRedisTemplate.delete("refresh_token" + userId)) {
            throw new RedisOperationException("Redis 登录信息删除失败");
        }

        // 3. 清除 SecurityContext
        SecurityContextHolder.clearContext();
    }

}
