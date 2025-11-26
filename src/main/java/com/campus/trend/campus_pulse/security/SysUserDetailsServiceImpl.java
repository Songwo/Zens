package com.campus.trend.campus_pulse.security;

import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<SysUser> user = sysUserService.searchByUsername(username);

        if (user.isEmpty()) {
            log.info("登录失败：用户"+username+"不存在(未找到)");
            throw new UsernameNotFoundException("用户【"+username+"】不存在或密码错误");
        }

        log.info("用户{}信息加载成功！Role：{}",user.get(0).getRole() == 0 ? "Admin":"User",user.get(0).getRole());

        return new AuthSysUser(user.get(0));
    }
}
