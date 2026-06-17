package com.campus.trend.campus_pulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 缓存预热服务
 * 应用启动时预加载热点数据到本地缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmer {

    private final SectionService sectionService;
    private final TagService tagService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        log.info("🔥 开始缓存预热...");
        long startTime = System.currentTimeMillis();

        try {
            // 预热板块列表（高频访问）
            warmUpSections();

            // 预热热门标签Top100（高频访问）
            warmUpHotTags();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("✅ 缓存预热完成，耗时: {}ms", elapsed);

        } catch (Exception e) {
            log.error("❌ 缓存预热失败: {}", e.getMessage(), e);
        }
    }

    private void warmUpSections() {
        try {
            var sections = sectionService.listActiveSections();
            log.info("预热板块列表: {}个", sections.size());
        } catch (Exception e) {
            log.warn("预热板块列表失败: {}", e.getMessage());
        }
    }

    private void warmUpHotTags() {
        try {
            var tags = tagService.listHotTags(100);
            log.info("预热热门标签: {}个", tags.size());
        } catch (Exception e) {
            log.warn("预热热门标签失败: ", e.getMessage());
        }
    }
}
