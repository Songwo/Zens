package com.campus.trend.campus_pulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 布隆过滤器初始化器
 * 应用启动时从数据库加载所有ID到布隆过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterInitializer implements CommandLineRunner {

    private final RBloomFilter<String> postIdBloomFilter;
    private final RBloomFilter<String> userIdBloomFilter;
    private final com.campus.trend.campus_pulse.mapper.PostMapper postMapper;
    private final com.campus.trend.campus_pulse.mapper.UserMapper userMapper;

    @Override
    public void run(String... args) {
        log.info("🔧 开始初始化布隆过滤器...");
        long startTime = System.currentTimeMillis();

        try {
            // 初始化帖子ID
            initPostIdBloomFilter();

            // 初始化用户ID
            initUserIdBloomFilter();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("✅ 布隆过滤器初始化完成，耗时: {}ms", elapsed);

        } catch (Exception e) {
            log.error("❌ 布隆过滤器初始化失败: {}", e.getMessage(), e);
        }
    }

    private void initPostIdBloomFilter() {
        try {
            // 只加载已发布的帖子ID
            List<String> postIds = postMapper.selectAllPublishedPostIds();

            if (postIds != null && !postIds.isEmpty()) {
                int count = 0;
                for (String postId : postIds) {
                    postIdBloomFilter.add(postId);
                    count++;

                    // 每1000条打印一次进度
                    if (count % 1000 == 0) {
                        log.debug("已加载 {} 个帖子ID到布隆过滤器", count);
                    }
                }
                log.info("帖子ID布隆过滤器: 已加载 {} 个ID", count);
            } else {
                log.info("帖子ID布隆过滤器: 暂无数据");
            }

        } catch (Exception e) {
            log.warn("初始化帖子ID布隆过滤器失败: {}", e.getMessage());
        }
    }

    private void initUserIdBloomFilter() {
        try {
            // 只加载正常状态的用户ID
            List<String> userIds = userMapper.selectAllActiveUserIds();

            if (userIds != null && !userIds.isEmpty()) {
                int count = 0;
                for (String userId : userIds) {
                    userIdBloomFilter.add(userId);
                    count++;

                    if (count % 1000 == 0) {
                        log.debug("已加载 {} 个用户ID到布隆过滤器", count);
                    }
                }
                log.info("用户ID布隆过滤器: 已加载 {} 个ID", count);
            } else {
                log.info("用户ID布隆过滤器: 暂无数据");
            }

        } catch (Exception e) {
            log.warn("初始化用户ID布隆过滤器失败: {}", e.getMessage());
        }
    }
}
