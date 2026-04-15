package com.campus.trend.campus_pulse.config;

import com.campus.trend.campus_pulse.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;

/**
 * STOMP 连接认证拦截器
 * 从 CONNECT 帧的 Authorization 或 X-User-Id header 中提取用户ID，
 * 设置为 Principal，使 convertAndSendToUser 能正确路由到目标用户
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String userId = null;

            // 1. 优先从 Authorization: Bearer <token> 提取
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    userId = jwtUtil.getUserId(token);
                } catch (Exception e) {
                    log.debug("[WS] JWT 解析失败: {}", e.getMessage());
                }
            }

            // 2. 降级：从 X-User-Id header 提取（非敏感场景）
            if (!StringUtils.hasText(userId)) {
                userId = accessor.getFirstNativeHeader("X-User-Id");
            }

            if (StringUtils.hasText(userId)) {
                final String finalUserId = userId;
                accessor.setUser(new Principal() {
                    @Override
                    public String getName() { return finalUserId; }
                });
                log.debug("[WS] 用户已认证: userId={}", finalUserId);
            }
        }

        return message;
    }
}
