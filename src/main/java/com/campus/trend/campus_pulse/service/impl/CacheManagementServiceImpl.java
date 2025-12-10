package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.service.CacheManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@Slf4j
public class CacheManagementServiceImpl implements CacheManagementService {

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public CacheManagementServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void clearAllTagCache() {
        long cleared = clearCacheByPattern("tag:*");
        log.info("已清除所有标签缓存，共 {} 个", cleared);
    }

    @Override
    public void clearAllTokenCache(){
        long access_cleared = clearCacheByPattern("access_token*");
        long refresh_cleared = clearCacheByPattern("refresh_token*");
        log.info("已清除所有Token缓存，共{}个", access_cleared+refresh_cleared);
    }

    @Override
    public long clearCacheByPattern(String keyPattern) {
        try {
            Set<String> keys = redisTemplate.keys(keyPattern);
            if (keys != null && !keys.isEmpty()) {
                Long deletedCount = redisTemplate.delete(keys);
                log.info("清除缓存模式 [{}]，共 {} 个键", keyPattern, deletedCount);
                return deletedCount != null ? deletedCount : 0;
            }
            return 0;
        } catch (Exception e) {
            log.error("清除缓存失败: {}", e.getMessage(), e);
            return 0;
        }
    }
}
