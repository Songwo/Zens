package com.campus.trend.campus_pulse.security;

import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.exception.custom.LoginException;
import com.campus.trend.campus_pulse.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserService userService;

    public UserDetailsServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws LoginException {
        User user = userService.lambdaQuery()
                .eq(User::getUsername, username)
                .one();

        if (user == null) {
            log.warn("用户 [{}] 登录失败：账号不存在", username);
            throw new LoginException("账号或密码错误");
        }

        log.info("用户 [{}] 信息加载成功，角色：{}", user.getUsername(), user.getRole());
        return new AuthUser(user);
    }
}
