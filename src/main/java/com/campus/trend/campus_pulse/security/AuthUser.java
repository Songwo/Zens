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

    @Getter
    private final User user;

    private final Collection<? extends GrantedAuthority> authorities;

    public AuthUser(User user) {
        this.user = user;
        String role = user.getRole() != null ? user.getRole() : "ROLE_USER";
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority(role));
    }

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

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
