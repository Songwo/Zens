package com.campus.trend.campus_pulse.config;

import org.redisson.Redisson;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置 - 分布式锁 + 布隆过滤器
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        String address = "redis://" + redisHost + ":" + redisPort;

        config.useSingleServer()
            .setAddress(address)
            .setDatabase(redisDatabase)
            .setConnectionPoolSize(64)
            .setConnectionMinimumIdleSize(10)
            .setConnectTimeout(10000)
            .setTimeout(3000)
            .setRetryAttempts(3)
            .setRetryInterval(1500);

        // 密码可选
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.useSingleServer().setPassword(redisPassword);
        }

        return Redisson.create(config);
    }

    /**
     * 帖子ID布隆过滤器 - 防止缓存击穿
     * 预期容量: 100万帖子
     * 误判率: 0.01 (1%)
     */
    @Bean
    public RBloomFilter<String> postIdBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("bloom:post:id");

        // 初始化布隆过滤器（如果未初始化）
        if (!bloomFilter.isExists()) {
            // 预期元素数量: 1,000,000，误判率: 0.01
            bloomFilter.tryInit(1_000_000L, 0.01);
        }

        return bloomFilter;
    }

    /**
     * 用户ID布隆过滤器
     * 预期容量: 10万用户
     * 误判率: 0.01 (1%)
     */
    @Bean
    public RBloomFilter<String> userIdBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("bloom:user:id");

        if (!bloomFilter.isExists()) {
            bloomFilter.tryInit(100_000L, 0.01);
        }

        return bloomFilter;
    }
}
