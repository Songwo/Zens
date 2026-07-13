package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.entity.SsoClient;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.service.SsoClientService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.service.OidcAuthorizationService;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OidcAuthorizationServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private SsoClientService clients;
    private UserService users;
    private JwtUtil jwt;
    private OidcAuthorizationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        clients = mock(SsoClientService.class);
        users = mock(UserService.class);
        jwt = mock(JwtUtil.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new OidcAuthorizationService(redis, clients, users, jwt);
    }

    @Test
    void authorizationCodeIsShortLivedAndBoundToClient() {
        User user = new User().setId("u-1").setUsername("song");
        when(users.getById("u-1")).thenReturn(user);
        when(clients.validateClient("zens-public-api", "https://pip.kdns.fr/oauth/oidc"))
                .thenReturn(new SsoClient());

        String code = service.issueAuthorizationCode(
                "u-1", "zens-public-api", "https://pip.kdns.fr/oauth/oidc", "profile email");

        assertNotNull(code);
        assertTrue(code.length() >= 40);
        verify(values).set(
                startsWith("sso:oidc:code:"),
                eq("u-1\nzens-public-api\nhttps://pip.kdns.fr/oauth/oidc\nprofile email"),
                eq(Duration.ofMinutes(2)));
    }

    @Test
    void tokenExchangeRejectsWrongClientSecretBeforeConsumingCode() {
        SsoClient client = new SsoClient().setClientSecret("correct-secret");
        when(clients.validateClient("zens-public-api", "https://pip.kdns.fr/oauth/oidc")).thenReturn(client);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.exchangeCode("code", "zens-public-api", "wrong-secret", "https://pip.kdns.fr/oauth/oidc"));

        assertEquals("客户端认证失败", error.getMessage());
        verify(values, never()).getAndDelete(anyString());
    }

    @Test
    void tokenExchangeConsumesCodeOnceAndReturnsBearerToken() {
        SsoClient client = new SsoClient().setClientSecret("correct-secret");
        User user = new User().setId("u-1").setUsername("song").setNickname("Song").setEmail("song@example.com");
        when(clients.validateClient("zens-public-api", "https://pip.kdns.fr/oauth/oidc")).thenReturn(client);
        when(values.getAndDelete("sso:oidc:code:one-time"))
                .thenReturn("u-1\nzens-public-api\nhttps://pip.kdns.fr/oauth/oidc\nprofile email");
        when(users.getById("u-1")).thenReturn(user);
        when(jwt.generateAccessToken(eq("u-1"), anyMap(), eq(false))).thenReturn("signed-token");
        when(jwt.getRemainingMillis("signed-token")).thenReturn(900_000L);

        Map<String, Object> token = service.exchangeCode(
                "one-time", "zens-public-api", "correct-secret", "https://pip.kdns.fr/oauth/oidc");

        assertEquals("signed-token", token.get("access_token"));
        assertEquals("Bearer", token.get("token_type"));
        assertEquals(900L, token.get("expires_in"));
        verify(values, times(1)).getAndDelete("sso:oidc:code:one-time");
    }

    @Test
    void authorizationRejectsUnsupportedOpenIdScope() {
        User user = new User().setId("u-1").setUsername("song");
        when(users.getById("u-1")).thenReturn(user);
        when(clients.validateClient("zens-public-api", "https://pip.kdns.fr/oauth/oidc"))
                .thenReturn(new SsoClient());

        assertThrows(IllegalArgumentException.class, () -> service.issueAuthorizationCode(
                "u-1", "zens-public-api", "https://pip.kdns.fr/oauth/oidc", "openid profile email"));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
