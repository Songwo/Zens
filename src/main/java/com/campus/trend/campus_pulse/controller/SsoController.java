package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.SsoClient;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.service.SsoClientService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;

/**
 * SSO 单点登录控制器
 *
 * 管理接口（管理员权限）：CRUD SSO 应用
 * 授权接口（已登录用户）：生成 SSO Token
 * 公开接口：查询应用公开信息
 */
@Slf4j
@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
public class SsoController {

    private final SsoClientService ssoClientService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Value("${jwt.secret}")
    private String jwtSecret;

    // =================== 管理接口 ===================

    /** 查询所有 SSO 应用 */
    @GetMapping("/clients")
    public Result<?> listClients() {
        List<SsoClient> clients = ssoClientService.listClients();
        // 脱敏：不返回完整 secret
        List<Map<String, Object>> result = new ArrayList<>();
        for (SsoClient c : clients) {
            result.add(clientToMap(c, false));
        }
        return Result.success(result);
    }

    /** 创建 SSO 应用 */
    @PostMapping("/clients")
    public Result<?> createClient(@RequestBody Map<String, String> body) {
        String clientId = body.get("clientId");
        String clientName = body.get("clientName");
        String redirectUri = body.get("redirectUri");
        String description = body.get("description");
        String logoUrl = body.get("logoUrl");

        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientName) || !StringUtils.hasText(redirectUri)) {
            return Result.failed("应用标识、名称和回调地址不能为空");
        }

        try {
            SsoClient client = ssoClientService.createClient(clientId, clientName, redirectUri, description, logoUrl);
            // 创建时返回完整 secret（唯一一次机会）
            return Result.success(clientToMap(client, true));
        } catch (RuntimeException e) {
            return Result.failed(e.getMessage());
        }
    }

    /** 更新 SSO 应用 */
    @PutMapping("/clients/{id}")
    public Result<?> updateClient(@PathVariable String id, @RequestBody Map<String, String> body) {
        String clientName = body.get("clientName");
        String redirectUri = body.get("redirectUri");
        String description = body.get("description");
        String logoUrl = body.get("logoUrl");

        if (!StringUtils.hasText(clientName) || !StringUtils.hasText(redirectUri)) {
            return Result.failed("名称和回调地址不能为空");
        }

        try {
            ssoClientService.updateClient(id, clientName, redirectUri, description, logoUrl);
            return Result.success("更新成功");
        } catch (RuntimeException e) {
            return Result.failed(e.getMessage());
        }
    }

    /** 删除 SSO 应用 */
    @DeleteMapping("/clients/{id}")
    public Result<?> deleteClient(@PathVariable String id) {
        try {
            ssoClientService.deleteClient(id);
            return Result.success("删除成功");
        } catch (RuntimeException e) {
            return Result.failed(e.getMessage());
        }
    }

    /** 启用/禁用 */
    @PostMapping("/clients/{id}/toggle")
    public Result<?> toggleClient(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return Result.failed("请指定启用/禁用状态");
        }
        try {
            ssoClientService.toggleClient(id, enabled);
            return Result.success(enabled ? "已启用" : "已禁用");
        } catch (RuntimeException e) {
            return Result.failed(e.getMessage());
        }
    }

    /** 重置密钥 */
    @PostMapping("/clients/{id}/reset-secret")
    public Result<?> resetSecret(@PathVariable String id) {
        try {
            String newSecret = ssoClientService.resetSecret(id);
            return Result.success(Map.of("clientSecret", newSecret));
        } catch (RuntimeException e) {
            return Result.failed(e.getMessage());
        }
    }

    // =================== 公开接口 ===================

    /** 根据 clientId 查询应用公开信息（供授权页显示） */
    @GetMapping("/clients/public/{clientId}")
    public Result<?> getPublicClientInfo(@PathVariable String clientId) {
        SsoClient client = ssoClientService.lambdaQuery()
                .eq(SsoClient::getClientId, clientId)
                .eq(SsoClient::getEnabled, 1)
                .one();
        if (client == null) {
            return Result.failed("应用不存在或已禁用");
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("clientId", client.getClientId());
        info.put("clientName", client.getClientName());
        info.put("description", client.getDescription());
        info.put("logoUrl", client.getLogoUrl());
        return Result.success(info);
    }

    // =================== SSO 授权接口 ===================

    /**
     * 生成 SSO Token（需要已登录）
     *
     * 前端授权页在用户确认后调用此接口，获取短时效 SSO Token，
     * 然后重定向到第三方应用的回调地址。
     */
    @PostMapping("/authorize")
    public Result<?> authorize(@RequestBody Map<String, String> body) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }

        String clientId = body.get("clientId");
        String redirectUri = body.get("redirectUri");

        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(redirectUri)) {
            return Result.failed("缺少必要参数");
        }

        // 验证 clientId 和 redirectUri 匹配
        try {
            ssoClientService.validateClient(clientId, redirectUri);
        } catch (RuntimeException e) {
            return Result.failed(e.getMessage());
        }

        // 查询用户完整信息
        User user = userService.getById(userId);
        if (user == null) {
            return Result.failed("用户不存在");
        }

        // 构建 SSO Token claims，包含丰富用户信息
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", user.getUsername());
        claims.put("nickname", StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        claims.put("avatar", user.getAvatar());
        claims.put("email", user.getEmail());
        claims.put("roles", List.of(StringUtils.hasText(user.getRole()) ? user.getRole() : "ROLE_USER"));
        claims.put("school", user.getSchool());
        claims.put("level", user.getLevel());
        claims.put("points", user.getPoints() != null ? user.getPoints() : 0);
        claims.put("trustLevel", user.getTrustLevel() != null ? user.getTrustLevel() : 0);
        claims.put("sso", true);
        claims.put("client_id", clientId);

        // 生成 5 分钟过期的短时效 SSO Token（直接使用 Jwts.builder 以覆盖默认过期时间）
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        Key signingKey = Keys.hmacShaKeyFor(keyBytes);
        long ssoExpireMs = 300_000; // 5 分钟

        String ssoToken = Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ssoExpireMs))
                .signWith(signingKey)
                .compact();

        log.info("SSO 授权: userId={}, clientId={}, redirectUri={}", userId, clientId, redirectUri);

        return Result.success(Map.of("ssoToken", ssoToken));
    }

    // =================== 辅助方法 ===================

    private Map<String, Object> clientToMap(SsoClient client, boolean showSecret) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", client.getId());
        map.put("clientId", client.getClientId());
        map.put("clientName", client.getClientName());
        if (showSecret) {
            map.put("clientSecret", client.getClientSecret());
        } else {
            // 只显示前8位
            String secret = client.getClientSecret();
            map.put("clientSecretMasked", secret != null && secret.length() > 8
                    ? secret.substring(0, 8) + "****"
                    : "****");
        }
        map.put("redirectUri", client.getRedirectUri());
        map.put("description", client.getDescription());
        map.put("logoUrl", client.getLogoUrl());
        map.put("enabled", client.getEnabled() != null && client.getEnabled() == 1);
        map.put("createTime", client.getCreateTime());
        map.put("updateTime", client.getUpdateTime());
        return map;
    }
}
