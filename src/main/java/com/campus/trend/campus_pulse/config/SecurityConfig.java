package com.campus.trend.campus_pulse.config;

import com.campus.trend.campus_pulse.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置类
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {


    private final JwtAuthenticationFilter  jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 密码编码器：使用 BCryptPasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt算法强度默认为10
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置安全过滤链：定义认证、授权、CORS
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. 配置 CORS 策略
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. 禁用 CSRF ,由于使用token所以不需要 CSRF 保护
                .csrf(AbstractHttpConfigurer::disable)

                // 3. 配置 Session 管理：设置为无状态 (STATELESS)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. 配置请求授权规则
                .authorizeHttpRequests(authorize -> authorize
                        // 允许匿名访问的接口（例如：注册、登录、公共内容、Swagger文档）
                        .requestMatchers(SecurityWhitelist.AUTH_WHITELIST).permitAll()
                        // 其他所有请求都需要认证 (Authenticated)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 配置 CORS (跨域资源共享) 策略
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许所有来源
        configuration.setAllowedOrigins(List.of("*"));
        // 允许所有请求方法 (GET, POST, PUT, DELETE)，最重要的需要允许 OPTIONS
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 允许所有请求头
        configuration.setAllowedHeaders(List.of("*"));
        // 是否允许发送 Cookie 或 HTTP 认证信息
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // 对所有路径生效
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}