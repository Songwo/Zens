package com.campus.trend.campus_pulse.config;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.filter.ApiPrefixRewriteFilter;
import com.campus.trend.campus_pulse.filter.InternalServiceFilter;
import com.campus.trend.campus_pulse.filter.JwtAuthenticationFilter;
import com.campus.trend.campus_pulse.filter.RequestSecurityFilter;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    @Value("${campus.security.allowed-origin-patterns:http://localhost,http://127.0.0.1,http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173}")
    private String allowedOriginPatterns;
    @Value("${campus.security.request-signature-required:false}")
    private boolean requestSignatureRequired;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(UserDetailsService uds,
                                                           JwtUtil jwtUtil,
                                                           StringRedisTemplate redisTemplate) {
        return new JwtAuthenticationFilter(uds, jwtUtil, redisTemplate);
    }

    @Bean
    public RequestSecurityFilter requestSecurityFilter(StringRedisTemplate redisTemplate, JwtUtil jwtUtil) {
        return new RequestSecurityFilter(redisTemplate, jwtUtil, objectMapper, requestSignatureRequired);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Song：配置安全过滤链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            ApiPrefixRewriteFilter apiPrefixRewriteFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RequestSecurityFilter requestSecurityFilter,
            InternalServiceFilter internalServiceFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> {})
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer
                                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; script-src 'self' https://challenges.cloudflare.com; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' https: ws: wss:; frame-src 'self' https://challenges.cloudflare.com; child-src 'self' https://challenges.cloudflare.com; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(SecurityWhitelist.AUTH_WHITELIST).permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/payment/callback/**", "/api/payment/callback/**").permitAll()
                        .requestMatchers("/api/static/**", "/api/uploads/**", "/api/ws/**").permitAll()
                        // 内部 s2s 接口由 InternalServiceFilter 单独 HMAC 鉴权,绕过 SecurityFilterChain 的认证
                        .requestMatchers("/api/internal/**").permitAll()
                        .requestMatchers("/ops-admin/**", "/api/ops-admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, SecurityWhitelist.PUBLIC_GET_URLS).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, SecurityWhitelist.PUBLIC_POST_URLS).permitAll()
                        .requestMatchers(
                                "/moderator/applications",
                                "/moderator/approve/**",
                                "/moderator/reject/**",
                                "/api/moderator/applications",
                                "/api/moderator/approve/**",
                                "/api/moderator/reject/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers(
                                "/admin",
                                "/admin/posts",
                                "/admin/posts/**",
                                "/admin/reports",
                                "/admin/reports/**",
                                "/admin/my-sections",
                                "/admin/my-sections/**",
                                "/admin/content",
                                "/admin/content/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_MODERATOR")
                        .requestMatchers("/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(apiPrefixRewriteFilter, UsernamePasswordAuthenticationFilter.class)
                // InternalServiceFilter 必须先于 JwtAuthenticationFilter,且会自己在 /api/internal/** 上做 HMAC 校验
                .addFilterAfter(internalServiceFilter, ApiPrefixRewriteFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, InternalServiceFilter.class)
                .addFilterAfter(requestSecurityFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Song：自定义 401 处理器：未认证
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.error(ResultCode.TOKEN_INVALID, "身份认证已过期或无效，请重新登录")));
        };
    }

    /**
     * Song：自定义 403 处理器：无权限
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.error(ResultCode.NO_PERMISSION, "您没有执行该操作的权限")));
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        List<String> finalOrigins = origins.isEmpty()
                ? List.of("http://localhost", "http://127.0.0.1", "http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:4173", "http://127.0.0.1:4173")
                : origins;
        configuration.setAllowedOriginPatterns(finalOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization", "X-Access-Token", "X-Access-Token-Expires-In"));
        log.info("CORS allowed origins: {}", finalOrigins);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
