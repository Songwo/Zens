package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.SsoClient;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.service.SsoClientService;
import com.campus.trend.campus_pulse.service.OidcAuthorizationService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

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
    private final com.campus.trend.campus_pulse.monitor.EcosystemMetrics ecosystemMetrics;
    private final OidcAuthorizationService oidcAuthorizationService;

    private static final String POINT_SHOP_CLIENT_ID = "zdc-shop";
    private static final String POINT_SHOP_CLIENT_NAME = "Zens 积分商城";
    private static final String POINT_SHOP_REDIRECT_URI = String.join(", ",
            "https://shop.allinsong.top/login/callback",
            "https://mall.allinsong.top/login/callback",
            "https://points.allinsong.top/login/callback",
            "https://zdc-shop.allinsong.top/login/callback",
            "http://localhost:3000/login/callback",
            "http://127.0.0.1:3000/login/callback",
            "http://localhost:3001/login/callback",
            "http://127.0.0.1:3001/login/callback"
    );
    private static final String POINT_SHOP_DESCRIPTION = "Zens 社区积分商城，使用主站账号单点登录并同步积分权益。";
    private static final String POINT_SHOP_LOGO_URL = "/logo.png";

    private static final String LOTTERY_CLIENT_ID = "campus-lottery-station";
    private static final String LOTTERY_CLIENT_NAME = "Zens 抽奖站";
    private static final String LOTTERY_REDIRECT_URI = String.join(", ",
            "https://lottery.allinsong.top/api/auth/sso/callback",
            "https://draw.allinsong.top/api/auth/sso/callback",
            "http://localhost:8093/api/auth/sso/callback",
            "http://127.0.0.1:8093/api/auth/sso/callback"
    );
    private static final String LOTTERY_DESCRIPTION = "Zens 社区原帖评论抽奖工具，使用主站账号登录，可同步评论、执行抽奖并把开奖结果回写原帖。";
    private static final String LOTTERY_LOGO_URL = "/logo.png";

    private static final String CDK_CLIENT_ID = "cdk-airdrop";
    private static final String CDK_CLIENT_NAME = "CDK 空投站";
    private static final String CDK_REDIRECT_URI = String.join(", ",
            "https://cdk.allinsong.top/login/callback",
            "https://airdrop.allinsong.top/login/callback",
            "http://localhost:8088/login/callback",
            "http://127.0.0.1:8088/login/callback"
    );
    private static final String CDK_DESCRIPTION = "Zens 社区 CDK 空投领取站，使用主站账号单点登录并发放兑换码与活动权益。";
    private static final String CDK_LOGO_URL = "/logo.png";

    private static final String PUBLIC_API_CLIENT_ID = "zens-public-api";
    private static final String PUBLIC_API_CLIENT_NAME = "Zens 公益 API 站";
    private static final String PUBLIC_API_REDIRECT_URI = "https://pip.kdns.fr/oauth/oidc";
    private static final String PUBLIC_API_DESCRIPTION = "Zens 公益 API 服务，使用社区账号登录并保持独立站内会话。";

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

    /** 一键创建或修复积分商城 SSO 应用 */
    @PostMapping("/clients/presets/point-shop")
    public Result<?> upsertPointShopClient() {
        return upsertPreset(POINT_SHOP_CLIENT_ID, POINT_SHOP_CLIENT_NAME,
                POINT_SHOP_REDIRECT_URI, POINT_SHOP_DESCRIPTION, POINT_SHOP_LOGO_URL);
    }

    /** 一键创建或修复抽奖站 SSO 应用 */
    @PostMapping("/clients/presets/lottery")
    public Result<?> upsertLotteryClient() {
        return upsertPreset(LOTTERY_CLIENT_ID, LOTTERY_CLIENT_NAME,
                LOTTERY_REDIRECT_URI, LOTTERY_DESCRIPTION, LOTTERY_LOGO_URL);
    }

    /** 一键创建或修复 CDK 空投站 SSO 应用 */
    @PostMapping("/clients/presets/cdk-airdrop")
    public Result<?> upsertCdkAirdropClient() {
        return upsertPreset(CDK_CLIENT_ID, CDK_CLIENT_NAME,
                CDK_REDIRECT_URI, CDK_DESCRIPTION, CDK_LOGO_URL);
    }

    /** 一键创建或修复公益 API 站 OAuth2 应用。 */
    @PostMapping("/clients/presets/public-api")
    public Result<?> upsertPublicApiClient() {
        return upsertPreset(PUBLIC_API_CLIENT_ID, PUBLIC_API_CLIENT_NAME,
                PUBLIC_API_REDIRECT_URI, PUBLIC_API_DESCRIPTION, "/logo.png");
    }

    /** preset 应用的通用 upsert：复用 ssoClientService.upsertPresetClient，幂等。 */
    private Result<?> upsertPreset(String clientId, String clientName, String redirectUri,
                                   String description, String logoUrl) {
        try {
            SsoClient before = ssoClientService.lambdaQuery()
                    .eq(SsoClient::getClientId, clientId)
                    .one();
            SsoClient client = ssoClientService.upsertPresetClient(
                    clientId, clientName, redirectUri, description, logoUrl);
            return Result.success(clientToMap(client, before == null));
        } catch (RuntimeException e) {
            return Result.failed(e.getMessage());
        }
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
        info.put("trusted", client.getTrusted() != null && client.getTrusted() == 1);
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
        String clientId = body.get("clientId");
        String redirectUri = body.get("redirectUri");

        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            ecosystemMetrics.recordSsoAuthorize(clientId, "not_login");
            return Result.failed("请先登录");
        }

        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(redirectUri)) {
            ecosystemMetrics.recordSsoAuthorize(clientId, "missing_params");
            return Result.failed("缺少必要参数");
        }

        // 验证 clientId 和 redirectUri 匹配
        try {
            ssoClientService.validateClient(clientId, redirectUri);
        } catch (RuntimeException e) {
            ecosystemMetrics.recordSsoAuthorize(clientId, "invalid_client");
            log.warn("SSO 授权被拒: userId={}, clientId={}, redirectUri={}, reason={}",
                    userId, clientId, redirectUri, e.getMessage());
            return Result.failed(e.getMessage());
        }

        // 查询用户完整信息
        User user = userService.getById(userId);
        if (user == null) {
            ecosystemMetrics.recordSsoAuthorize(clientId, "user_not_found");
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

        ecosystemMetrics.recordSsoAuthorize(clientId, "success");
        log.info("SSO 授权: userId={}, clientId={}, redirectUri={}, result=success", userId, clientId, redirectUri);

        return Result.success(Map.of("ssoToken", ssoToken));
    }

    /**
     * OAuth2 浏览器入口。保留 /oidc 路径兼容公益站既有回调 slug。
     */
    @GetMapping("/oidc/authorize")
    public ResponseEntity<?> oidcAuthorizeStart(
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("response_type") String responseType,
            @RequestParam(defaultValue = "profile email") String scope,
            @RequestParam String state) {
        if (!"code".equals(responseType) || !isSafeState(state) || !isSupportedOAuthScope(scope)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_request"));
        }
        try {
            ssoClientService.validateClient(clientId, redirectUri);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_client"));
        }
        String location = UriComponentsBuilder.fromPath("/sso/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scope)
                .queryParam("state", state)
                .queryParam("protocol", "oidc")
                .build().encode().toUriString();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }

    /** 已登录用户确认后签发一次性 OAuth2 授权码。 */
    @PostMapping("/oidc/authorize")
    public ResponseEntity<Result<?>> oidcAuthorize(@RequestBody Map<String, String> body) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return sensitive(Result.failed("请先登录"));
        }
        String clientId = body.get("clientId");
        String redirectUri = body.get("redirectUri");
        String state = body.get("state");
        String scope = body.get("scope");
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(redirectUri)
                || !isSafeState(state) || !isSupportedOAuthScope(scope)) {
            return sensitive(Result.failed("缺少或不支持的 OAuth 参数"));
        }
        try {
            String code = oidcAuthorizationService.issueAuthorizationCode(userId, clientId, redirectUri, scope);
            String callback = UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("code", code)
                    .queryParam("state", state)
                    .build().encode().toUriString();
            return sensitive(Result.success(Map.of("redirectUrl", callback)));
        } catch (RuntimeException e) {
            return sensitive(Result.failed(e.getMessage()));
        }
    }

    /** 拒绝授权时同样由服务端校验回调地址，禁止前端 query 参数形成开放重定向。 */
    @PostMapping("/oidc/deny")
    public ResponseEntity<Result<?>> denyOAuthAuthorization(@RequestBody Map<String, String> body) {
        String clientId = body.get("clientId");
        String redirectUri = body.get("redirectUri");
        String state = body.get("state");
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(redirectUri) || !isSafeState(state)) {
            return sensitive(Result.failed("缺少必要参数"));
        }
        try {
            ssoClientService.validateClient(clientId, redirectUri);
            String callback = UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("error", "access_denied")
                    .queryParam("state", state)
                    .build().encode().toUriString();
            return sensitive(Result.success(Map.of("redirectUrl", callback)));
        } catch (RuntimeException e) {
            return sensitive(Result.failed("客户端或回调地址无效"));
        }
    }

    /** OAuth2 授权码换取短时 Bearer Token，客户端必须使用服务端密钥认证。 */
    @PostMapping(value = "/oidc/token", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<?> oidcToken(
            @RequestParam("grant_type") String grantType,
            @RequestParam String code,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam("redirect_uri") String redirectUri) {
        if (!"authorization_code".equals(grantType)) {
            return oauthSensitive(HttpStatus.BAD_REQUEST, Map.of("error", "unsupported_grant_type"));
        }
        try {
            return oauthSensitive(HttpStatus.OK,
                    oidcAuthorizationService.exchangeCode(code, clientId, clientSecret, redirectUri));
        } catch (RuntimeException e) {
            return oauthSensitive(HttpStatus.BAD_REQUEST, Map.of("error", "invalid_grant"));
        }
    }

    /** OAuth2 userinfo：只接受专为公益站 OAuth 流程签发的访问令牌。 */
    @GetMapping("/oidc/userinfo")
    public ResponseEntity<?> oidcUserInfo(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return oauthSensitive(HttpStatus.UNAUTHORIZED, Map.of("error", "invalid_token"));
        }
        try {
            return oauthSensitive(HttpStatus.OK, oidcAuthorizationService.userInfo(authorization.substring(7)));
        } catch (RuntimeException e) {
            return oauthSensitive(HttpStatus.UNAUTHORIZED, Map.of("error", "invalid_token"));
        }
    }

    private boolean isSupportedOAuthScope(String scope) {
        if (!StringUtils.hasText(scope)) return true;
        return Arrays.stream(scope.trim().split("\\s+"))
                .allMatch(item -> "profile".equals(item) || "email".equals(item));
    }

    private boolean isSafeState(String state) {
        return StringUtils.hasText(state) && state.length() <= 512
                && state.chars().noneMatch(Character::isISOControl);
    }

    private ResponseEntity<Result<?>> sensitive(Result<?> body) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(body);
    }

    private ResponseEntity<?> oauthSensitive(HttpStatus status, Object body) {
        return ResponseEntity.status(status)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
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
        map.put("trusted", client.getTrusted() != null && client.getTrusted() == 1);
        map.put("createTime", client.getCreateTime());
        map.put("updateTime", client.getUpdateTime());
        return map;
    }
}
