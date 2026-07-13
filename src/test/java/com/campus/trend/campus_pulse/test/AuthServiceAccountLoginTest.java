package com.campus.trend.campus_pulse.test;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.campus.trend.campus_pulse.common.exception.LoginException;
import com.campus.trend.campus_pulse.dto.request.LoginReq;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.service.InviteCodeService;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.service.VerificationCodeService;
import com.campus.trend.campus_pulse.service.impl.AuthServiceImpl;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceAccountLoginTest {

    private PasswordEncoder passwordEncoder;
    private UserService userService;
    private AuthServiceImpl authService;
    private LambdaQueryChainWrapper<User> query;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        passwordEncoder = mock(PasswordEncoder.class);
        userService = mock(UserService.class);
        query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        doReturn(query).when(query).eq(any(), any());
        when(userService.lambdaQuery()).thenReturn(query);
        authService = new AuthServiceImpl(
                passwordEncoder,
                mock(StringRedisTemplate.class),
                mock(JwtUtil.class),
                userService,
                mock(VerificationCodeService.class),
                mock(LevelService.class),
                mock(NotificationService.class),
                new ObjectMapper(),
                mock(InviteCodeService.class),
                mock(SimpMessagingTemplate.class));
    }

    @Test
    void passwordLogin_shouldTrimAndResolveUsernameDirectly() throws Exception {
        User user = new User().setId("u1").setUsername("Song").setPassword("encoded");
        when(query.one()).thenReturn(user);
        when(passwordEncoder.matches("123456sss", "encoded")).thenReturn(true);

        LoginReq request = passwordRequest("  Song  ", "123456sss");

        assertSame(user, invokePasswordLogin(request));
        verify(query).eq(any(SFunction.class), eq("Song"));
    }

    @Test
    void passwordLogin_shouldNormalizeEmailWithoutChangingPassword() throws Exception {
        User user = new User().setId("u2").setEmail("song@example.com").setPassword("encoded");
        when(query.one()).thenReturn(user);
        when(passwordEncoder.matches("CaseSensitive!", "encoded")).thenReturn(true);

        assertSame(user, invokePasswordLogin(passwordRequest(" Song@Example.COM ", "CaseSensitive!")));
        verify(query).eq(any(SFunction.class), eq("song@example.com"));
        verify(passwordEncoder).matches("CaseSensitive!", "encoded");
    }

    @Test
    void passwordLogin_shouldUseGenericErrorAndDummyHashForUnknownAccount() {
        when(query.one()).thenReturn(null);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        LoginException error = assertThrows(LoginException.class,
                () -> invokePasswordLogin(passwordRequest("missing_user", "secret123")));

        assertEquals("用户名或密码错误", error.getMessage());
        verify(passwordEncoder).matches(eq("secret123"), anyString());
    }

    private LoginReq passwordRequest(String account, String password) {
        LoginReq request = new LoginReq();
        request.setLoginType("password");
        request.setAccount(account);
        request.setPassword(password);
        return request;
    }

    private User invokePasswordLogin(LoginReq request) throws Exception {
        Method method = AuthServiceImpl.class.getDeclaredMethod("loginByPassword", LoginReq.class);
        method.setAccessible(true);
        try {
            return (User) method.invoke(authService, request);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }
}
