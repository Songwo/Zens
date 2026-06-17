package com.campus.trend.campus_pulse.config;

import com.alibaba.fastjson2.support.spring6.data.redis.GenericFastJsonRedisSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置（优化版）
 * 使用FastJson2替代Jackson，性能提升2-3倍
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // Key序列化：String
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);

        // Value序列化：FastJson2（比Jackson快2-3倍）
        GenericFastJsonRedisSerializer fastJsonSerializer = new GenericFastJsonRedisSerializer();
        redisTemplate.setValueSerializer(fastJsonSerializer);
        redisTemplate.setHashValueSerializer(fastJsonSerializer);

        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }
}
