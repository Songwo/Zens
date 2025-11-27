package com.campus.trend.campus_pulse.filter;

import com.campus.trend.campus_pulse.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    public JwtAuthenticationFilter(UserDetailsService userDetailsService,
                                   JwtUtil jwtUtil,
                                   StringRedisTemplate stringRedisTemplate) {
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {

        String accessToken = jwtUtil.getToken(request);

        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = jwtUtil.getUserId(accessToken);
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 从 Redis
        String redisAccess = stringRedisTemplate.opsForValue().get("access_token"+userId);
        String redisRefresh = stringRedisTemplate.opsForValue().get("refresh_token"+userId);

        // accessToken 过期 → 刷新
        if (!jwtUtil.validate(accessToken)) {
            if (redisRefresh != null && jwtUtil.validate(redisRefresh)) {

                Claims c = jwtUtil.parse(redisRefresh);

                String newAccess = jwtUtil.generateAccessToken(userId, c);

                stringRedisTemplate.opsForValue().set("access_token"+userId,newAccess);

                response.setHeader("New-Access-Token", newAccess);

                accessToken = newAccess;

            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Token expired, please login again.");
                return;
            }
        }

        // accessToken 必须匹配 redisAccessToken
        if (!accessToken.equals(redisAccess)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Security 未认证，执行认证
        String username = jwtUtil.getUsername(accessToken);
        if (username != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails ud = userDetailsService.loadUserByUsername(username);

            if (jwtUtil.validate(accessToken, ud)) {

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                ud,
                                null,
                                ud.getAuthorities()
                        );

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.info("认证通过");
            }
        }

        filterChain.doFilter(request, response);
    }
}
