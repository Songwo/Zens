package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.dto.request.LoginRequest;
import com.campus.trend.campus_pulse.dto.request.RegisterRequest;
import com.campus.trend.campus_pulse.dto.response.LoginResponse;
import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.exception.definexception.LoginException;
import com.campus.trend.campus_pulse.exception.definexception.RedisDeleteException;
import com.campus.trend.campus_pulse.exception.definexception.RegisterException;
import com.campus.trend.campus_pulse.exception.definexception.UserNameAlreadyExisted;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.UserProfileService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.GenerateIDUtil;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
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

    public AuthServiceImpl(AuthenticationManager authorizationManager,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate stringRedisTemplate,
            JwtUtil jwtUtil, UserService userService,
            UserProfileService userProfileService) {
        this.authorizationManager = authorizationManager;
        this.passwordEncoder = passwordEncoder;
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.userProfileService = userProfileService;
    }

    @Override
    public LoginResponse Login(LoginRequest req) {

        // 1. 构�?Token（账户密码封装）
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(req.getUsername(),
                req.getPassword());

        // 2. SecurityManager 进行实际认证
        Authentication authentication = authorizationManager.authenticate(authToken);

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new LoginException("登录失败，账号或密码错误");
        }

        // 3. 获取登录用户信息
        AuthSysUser authUser = (AuthSysUser) authentication.getPrincipal();
        SysUser user = authUser.getSysUser();

        // 4. JWT 内容,构造自定义 JWT
        Map<String, Object> claims = jwtUtil.buildClaims(user.getUsername(), user.getRole(), user.getAvatar());

        // 5. 生成 Token ,并存�?Redis
        String AccessToken = jwtUtil.generateAccessToken(user.getId(), claims);
        String RefreshToken = jwtUtil.generateRefreshToken(user.getId(), claims);

        LoginResponse response = new LoginResponse();
        response.setAccessToken(AccessToken);
        response.setRefreshToken(RefreshToken);

        // 5.1 构建 Redis ，通过Key-用户ID，Value-token
        stringRedisTemplate.opsForValue().set("access_token" + user.getId(), AccessToken);
        stringRedisTemplate.opsForValue().set("refresh_token" + user.getId(), RefreshToken);

        userService.autoUpgradeGrade(user);

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void Register(RegisterRequest req) {

        // 1. 用户名（学号）是否已存在
        SysUser exist = userService.lambdaQuery()
                .eq(SysUser::getUsername, req.getUsername())
                .one();

        if (exist != null) {
            throw new UserNameAlreadyExisted("该学号已注册");
        }

        // 2. 创建用户实体
        SysUser user = new SysUser();

        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setAvatar(req.getAvatar());
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
    public void Logout() {
        // 1. 获取当前登录用户
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        // 2. 删除 Redis Token
        if (!stringRedisTemplate.delete("access_token" + userId)
                || !stringRedisTemplate.delete("refresh_token" + userId)) {
            throw new RedisDeleteException("Redis 登录信息删除失败");
        }

        // 3. 清除 SecurityContext
        SecurityContextHolder.clearContext();
    }

}
