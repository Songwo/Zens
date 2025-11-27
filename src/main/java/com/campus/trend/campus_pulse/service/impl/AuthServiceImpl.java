package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.dto.LoginRequest;
import com.campus.trend.campus_pulse.dto.RegisterRequest;
import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.exception.definexception.LoginException;
import com.campus.trend.campus_pulse.exception.definexception.RegisterException;
import com.campus.trend.campus_pulse.exception.definexception.UserNameAlreadyExisted;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.SysUserService;
import com.campus.trend.campus_pulse.utils.GenerateIDUtil;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authorizationManager;

    private final SysUserService sysUserService;

    private final PasswordEncoder passwordEncoder;

    private final StringRedisTemplate stringRedisTemplate;

    public AuthServiceImpl(AuthenticationManager authorizationManager,
                           SysUserService sysUserService,
                           PasswordEncoder passwordEncoder, StringRedisTemplate stringRedisTemplate) {
        this.authorizationManager = authorizationManager;
        this.sysUserService = sysUserService;
        this.passwordEncoder = passwordEncoder;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public String login(LoginRequest req) {

        // 1. 构造 Token（账户密码封装）
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword());

        // 2. SecurityManager 进行实际认证
        Authentication authentication = authorizationManager.authenticate(authToken);

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new LoginException("登录失败，账号或密码错误");
        }

        // 3. 获取登录用户信息
        AuthSysUser authUser = (AuthSysUser) authentication.getPrincipal();
        SysUser user = authUser.getSysUser();

        // 4. JWT 内容,构造自定义 JWT
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", user.getUsername());
        claims.put("avatar", user.getAvatar());
        claims.put("role", user.getRole());

        // 5. 生成 Token ,并存入 Redis
        String token = JwtUtil.GenerateToken(user.getId(), claims);

        // 5.1 构建 Redis ，通过Key-用户ID，Value-token
        stringRedisTemplate.opsForValue().set(user.getId(),token);

        return token;
    }

    @Override
    public void register(RegisterRequest req) {

        // 1. 用户名（学号）是否已存在
        SysUser exist = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, req.getUsername())
                .one();

        if (exist != null) {
            throw new UserNameAlreadyExisted("该学号已注册");
        }

        // 2. 创建用户实体
        SysUser user = new SysUser();
        user.setId(GenerateIDUtil.genId("USER"));
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setAvatar(req.getAvatar());
        user.setMajor(req.getMajor());
        user.setGrade(req.getGrade());

        // 3. 保存
        boolean saved = sysUserService.save(user);
        if (!saved) {
            throw new RegisterException("注册失败，请稍后重试");
        }
    }


}
