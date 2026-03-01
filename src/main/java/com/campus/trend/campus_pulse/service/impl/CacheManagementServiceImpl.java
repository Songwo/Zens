package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.service.CacheManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
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
        long new_access_cleared = clearCacheByPattern("auth:access:*");
        long new_refresh_cleared = clearCacheByPattern("auth:refresh:*");
        long new_device_cleared = clearCacheByPattern("auth:device:*");
        long nonce_cleared = clearCacheByPattern("auth:req:nonce:*");
        log.info("已清除所有Token缓存，共{}个",
                access_cleared + refresh_cleared + new_access_cleared + new_refresh_cleared + new_device_cleared + nonce_cleared);
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

    @Override
    public Map<String, Long> getCacheOverview() {
        Map<String, Long> overview = new LinkedHashMap<>();

        long tagHot = countCacheByPattern("tag:hot:*");
        long postFeed = countCacheByPattern("post:feed:cache:*");
        long postFeedVersion = countCacheByPattern("post:feed:version:*");
        long userRecommend = countCacheByPattern("user:recommend:*");

        long legacyAccess = countCacheByPattern("access_token*");
        long legacyRefresh = countCacheByPattern("refresh_token*");
        long access = countCacheByPattern("auth:access:*");
        long refresh = countCacheByPattern("auth:refresh:*");
        long device = countCacheByPattern("auth:device:*");
        long nonce = countCacheByPattern("auth:req:nonce:*");
        long tokenTotal = legacyAccess + legacyRefresh + access + refresh + device + nonce;

        long captcha = countCacheByPattern("auth:captcha:*");
        long lock = countCacheByPattern("auth:lock:*");

        overview.put("tagHot", tagHot);
        overview.put("postFeed", postFeed);
        overview.put("postFeedVersion", postFeedVersion);
        overview.put("userRecommend", userRecommend);
        overview.put("tokenTotal", tokenTotal);
        overview.put("captcha", captcha);
        overview.put("lock", lock);
        overview.put("legacyAccess", legacyAccess);
        overview.put("legacyRefresh", legacyRefresh);
        overview.put("authAccess", access);
        overview.put("authRefresh", refresh);
        overview.put("authDevice", device);
        overview.put("requestNonce", nonce);

        long total = 0L;
        for (Long v : overview.values()) {
            total += v == null ? 0L : v;
        }
        overview.put("total", total);
        return overview;
    }

    @Override
    public long countCacheByPattern(String keyPattern) {
        try {
            Set<String> keys = redisTemplate.keys(keyPattern);
            return keys == null ? 0L : keys.size();
        } catch (Exception e) {
            log.error("统计缓存失败: pattern={}, err={}", keyPattern, e.getMessage(), e);
            return 0L;
        }
    }
}
