package com.campus.trend.campus_pulse.scheduled;

import com.campus.trend.campus_pulse.entity.SysTag;
import com.campus.trend.campus_pulse.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 标签热度衰减定时任务
 * 每小时执行一次，更新标签的衰减后热度
 */
@Component
@Slf4j
public class TagHeatDecayTask {

    private final TagService tagService;

    // 衰减周期（天）
    private static final double DECAY_PERIOD = 7.0;
    // 衰减速率
    private static final double DECAY_RATE = 1.5;

    @Autowired
    public TagHeatDecayTask(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * 每小时执行一次热度衰减
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void updateHeatScores() {
        log.info("开始执行标签热度衰减任务");

        try {
            List<SysTag> allTags = tagService.list();
            int updatedCount = 0;

            for (SysTag tag : allTags) {
                if (tag.getHeat() != null && tag.getHeat() > 0) {
                    // 计算衰减后的热度
                    double decayedHeat = calculateDecayedHeat(tag);

                    // 只在热度变化时更新
                    if (Math.abs(decayedHeat - tag.getHeat()) >= 1.0) {
                        tag.setHeat((int) decayedHeat);
                        tagService.updateById(tag);
                        updatedCount++;
                    }
                }
            }

            log.info("标签热度衰减任务完成，共更新 {} 个标签", updatedCount);

        } catch (Exception e) {
            log.error("标签热度衰减任务执行失败", e);
        }
    }

    /**
     * 计算衰减后的热度
     *
     * @param tag 标签
     * @return 衰减后的热度值
     */
    private double calculateDecayedHeat(SysTag tag) {
        if (tag.getCreateTime() == null) {
            return tag.getHeat();
        }

        // 计算标签年龄（天数）
        Duration age = Duration.between(tag.getCreateTime(), LocalDateTime.now());
        double ageInDays = age.toDays();

        // 基础热度
        double baseHeat = tag.getHeat();

        // 时间衰减因子
        // timeFactor = 1 / (1 + ageInDays / decayPeriod) ^ decayRate
        double timeFactor = 1.0 / Math.pow(1 + (ageInDays / DECAY_PERIOD), DECAY_RATE);

        // 衰减后热度 = 基础热度 × 时间因子
        double decayedHeat = baseHeat * timeFactor;

        // 最小值为0
        return Math.max(0, decayedHeat);
    }

    /**
     * 手动触发热度衰减（用于测试）
     */
    public void triggerManually() {
        log.info("手动触发标签热度衰减");
        updateHeatScores();
    }
}
