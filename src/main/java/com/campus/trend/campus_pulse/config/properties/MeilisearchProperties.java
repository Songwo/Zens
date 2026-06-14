package com.campus.trend.campus_pulse.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Meilisearch 中文搜索引擎配置
 * enabled=false 时搜索走 MySQL FULLTEXT 优雅降级
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.meilisearch")
public class MeilisearchProperties {

    private boolean enabled = false;
    private String host = "http://localhost:7700";
    private String apiKey = "";
    private String postsIndex = "posts";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
}
