package com.campus.trend.campus_pulse.security;

import com.campus.trend.campus_pulse.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

public class AuthUser implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 实际存储的用户信息
     * 通过Lombok的@Getter注解返回用户的Get方法，方便在业务层直接获取用户信息
     */
    @Getter
    private final User user;

    public AuthUser(User user) {
        this.user = user;
        String roleName = user.getRole() == 0 ? "ROLE_ADMIN" : "ROLE_STUDENT";
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority(roleName));
    }

    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * 用户权限列表
     * 0 - 管理员 1 - 普通用户
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * 账户是否过期
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账户是否被锁定
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 凭证是否过期
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账户是否可用
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

}
