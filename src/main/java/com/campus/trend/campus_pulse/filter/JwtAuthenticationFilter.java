package com.campus.trend.campus_pulse.filter;

import com.campus.trend.campus_pulse.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;

    private final JwtUtil jwtUtil;

    private final StringRedisTemplate stringRedisTemplate;

    public JwtAuthenticationFilter(UserDetailsService userDetailsService, JwtUtil jwtUtil, StringRedisTemplate stringRedisTemplate) {
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
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

        // token 为空直接放行
        if (token == null) {
            filterChain.doFilter(request,response);
            return;
        }

        //2. 从 Token 提取用户 ID
        String userID = jwtUtil.getUserID(token);
        if (userID == null) {
            filterChain.doFilter(request,response);
            return;
        }

        //3. 校验 Redis 中的 Token 是否一致
        String redisToken = stringRedisTemplate.opsForValue().get(userID);
        if (!token.equals(redisToken)) {
            filterChain.doFilter(request,response);
            return;
        }

        //4. 从 Token 中获取用户名
        String username = jwtUtil.getUsername(token);

        //5. Security 上下文未认证时，进行认证
        if (username != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            //6. 验证 Token 的有效性
            if (jwtUtil.validateToken(token, userDetails)) {

                // 创建一个已经通过认证的对象
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                // 1.用户基本信息
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

        // 放行
        filterChain.doFilter(request,response);
    }
}
