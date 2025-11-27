package com.campus.trend.campus_pulse.security;

import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.exception.definexception.LoginException;
import com.campus.trend.campus_pulse.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class SysUserDetailsServiceImpl implements UserDetailsService {

    /**
     * 构造方法注入用户方法
    */
    private final SysUserService sysUserService;

    public SysUserDetailsServiceImpl(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws LoginException {

        SysUser user = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, username)
                .one();

        if (user == null) {
            log.warn("用户 [{}] 登录失败：账号不存在", username);
            throw new LoginException("账号或密码错误");
        }

        log.info("用户 [{}] 信息加载成功，角色：{}",
                user.getUsername(),
                user.getRole() == 0 ? "Admin" : "User");

        return new AuthSysUser(user);
    }
}
