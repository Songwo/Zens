package com.campus.trend.campus_pulse.config;

import com.campus.trend.campus_pulse.config.properties.MeilisearchProperties;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Meilisearch 客户端配置。仅当 campus.meilisearch.enabled=true 时创建 bean，
 * 否则搜索服务自动降级到 MySQL FULLTEXT（见 FulltextSearchServiceImpl）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.meilisearch.enabled", havingValue = "true")
public class MeilisearchConfig {

    private final MeilisearchProperties props;

    @Bean
    public Client meilisearchClient() {
        log.info("初始化 Meilisearch 客户端, host={}, index={}", props.getHost(), props.getPostsIndex());
        return new Client(new Config(props.getHost(), props.getApiKey()));
    }
}
