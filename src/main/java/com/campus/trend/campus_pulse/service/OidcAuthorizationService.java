package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.entity.SsoClient;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 第一方子站 OAuth2 授权码流程。类名及 /oidc 路径为兼容既有公益站配置而保留。
 */
@Service
@RequiredArgsConstructor
public class OidcAuthorizationService {

    private static final String CODE_KEY_PREFIX = "sso:oidc:code:";
    private static final Duration CODE_TTL = Duration.ofMinutes(2);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final SsoClientService ssoClientService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public String issueAuthorizationCode(String userId, String clientId, String redirectUri, String scope) {
        ssoClientService.validateClient(clientId, redirectUri);
        if (userService.getById(userId) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        String code = randomToken(32);
        String value = String.join("\n", userId, clientId, redirectUri, normalizeScope(scope));
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + code, value, CODE_TTL);
        return code;
    }

    public Map<String, Object> exchangeCode(String code, String clientId, String clientSecret, String redirectUri) {
        SsoClient client = ssoClientService.validateClient(clientId, redirectUri);
        if (!constantTimeEquals(client.getClientSecret(), clientSecret)) {
            throw new IllegalArgumentException("客户端认证失败");
        }

        String key = CODE_KEY_PREFIX + code;
        String stored = redisTemplate.opsForValue().getAndDelete(key);
        if (!StringUtils.hasText(stored)) {
            throw new IllegalArgumentException("授权码无效或已过期");
        }
        String[] parts = stored.split("\\n", -1);
        if (parts.length != 4 || !clientId.equals(parts[1]) || !redirectUri.equals(parts[2])) {
            throw new IllegalArgumentException("授权码与客户端不匹配");
        }

        User user = userService.getById(parts[0]);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        Map<String, Object> claims = userClaims(user, parts[3]);
        claims.put("token_use", "oidc_access");
        claims.put("client_id", clientId);
        claims.put("scope", parts[3]);
        String accessToken = jwtUtil.generateAccessToken(user.getId(), claims, false);
        // OAuth2 的 expires_in 是标准 JSON 数值。项目全局会将 Long 序列化为字符串
        // 以保护前端大整数精度，但 Go oauth2 客户端会因此拒绝整个 Token 响应。
        // 这里使用 Integer（有效期为秒，远低于上限）保持协议兼容。
        int expiresIn = (int) Math.min(Integer.MAX_VALUE,
                Math.max(1L, jwtUtil.getRemainingMillis(accessToken) / 1000L));
        return Map.of(
                "access_token", accessToken,
                "token_type", "Bearer",
                "expires_in", expiresIn,
                "scope", parts[3]
        );
    }

    public Map<String, Object> userInfo(String accessToken) {
        Claims claims = jwtUtil.parse(accessToken);
        if (claims == null || !"oidc_access".equals(claims.get("token_use", String.class))) {
            throw new IllegalArgumentException("访问令牌无效或已过期");
        }
        User user = userService.getById(claims.getSubject());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        String scope = claims.get("scope", String.class);
        Map<String, Object> info = userClaims(user, scope);
        info.put("sub", user.getId());
        return info;
    }

    private Map<String, Object> userClaims(User user) {
        return userClaims(user, "profile email");
    }

    private Map<String, Object> userClaims(User user, String scope) {
        Map<String, Object> claims = new HashMap<>();
        List<String> scopes = StringUtils.hasText(scope) ? List.of(scope.split("\\s+")) : List.of();
        if (scopes.contains("profile")) {
            String username = user.getUsername();
            claims.put("username", username);
            claims.put("preferred_username", username);
            claims.put("name", StringUtils.hasText(user.getNickname()) ? user.getNickname() : username);
            if (StringUtils.hasText(user.getAvatar())) claims.put("picture", user.getAvatar());
        }
        if (scopes.contains("email") && StringUtils.hasText(user.getEmail())) {
            claims.put("email", user.getEmail());
            claims.put("email_verified", true);
        }
        return claims;
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) return "profile email";
        List<String> requested = List.of(scope.trim().split("\\s+"));
        if (requested.stream().anyMatch(item -> !"profile".equals(item) && !"email".equals(item))) {
            throw new IllegalArgumentException("不支持的 OAuth scope");
        }
        return requested.stream().distinct().reduce((left, right) -> left + " " + right).orElse("profile email");
    }

    private String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
