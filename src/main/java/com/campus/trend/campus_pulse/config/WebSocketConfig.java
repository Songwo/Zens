package com.campus.trend.campus_pulse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Song：说明
 * Song：用于实时推送帖子更新、新回复、浏览量变化等事件
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Song：启用简单消息代理，用于向客户端推送消息
        // Song：说明
        // Song：说明
        config.enableSimpleBroker("/topic", "/queue");

        // Song：客户端发送消息的前缀（如果需要客户端向服务器发送消息）
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Song：说明
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Song：允许跨域（生产环境应配置具体域名）
                .withSockJS(); // Song：说明
    }
}
