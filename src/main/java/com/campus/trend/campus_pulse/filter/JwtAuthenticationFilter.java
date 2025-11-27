package com.campus.trend.campus_pulse.filter;

import com.campus.trend.campus_pulse.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(UserDetailsService userDetailsService, JwtUtil jwtUtil) {
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
    }
    /**
     * 重写OncePerRequestFilter的方法，实现
     * 对不合法的请求进行过滤拦截
    */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        //1.获取Token
        String token = jwtUtil.getToken(request);
            // 如果token不是空的，就获取token中的用户名
            if (token != null) {
                String username = JwtUtil.getUsername(token);
                // 如果获取到的用户名也不是空的，并且通过了SpringSecurity的身份认证，Security上下文不为空，那么就加载用户信息
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    // 验证token和当前的用户是否有效
                    if (jwtUtil.validateToken(token,userDetails)){
                        // 创建一个已经通过认证的对象
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        // 1.童虎基本信息
                                        userDetails,
                                        // 2.Jwt不需要密码所以为null
                                        null,
                                        // 3.获取当前用户的认证角色身份
                                        userDetails.getAuthorities()
                                );
                        // 附带客户端安全信息，包括IP等信息
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );
                        // 将用户存入Security的上下文
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
            // 过滤完成交给下一个中间件过滤器
            filterChain.doFilter(request,response);
    }
}
