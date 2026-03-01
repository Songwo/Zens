package com.campus.trend.campus_pulse.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子热度衰减定时任务
 *
 * 算法说明（Hacker News 改良版 + 情感加权）：
 * heatScore = interactionScore / (1 + ageInHours / 24) ^ GRAVITY
 *
 * 其中:
 * interactionScore = viewCount * 0.5
 * + likeCount * 3
 * + commentCount * 5
 * + collectCount * 2
 *
 * GRAVITY = 1.5 (衰减重力常数，越大衰减越快)
 *
 * 附加调节：
 * - 情感得分 < 0.3 的低质量/负面帖子，施加 0.7 倍惩罚
 * - 精华帖额外 +20 基础分
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PostHeatDecayTask {

    private final PostMapper postMapper;

    /** 衰减重力常数 */
    private static final double GRAVITY = 1.5;

    /** 只处理最近 14 天内创建的帖子 */
    private static final int ACTIVE_DAYS = 14;

    /** 负面内容惩罚阈值 */
    private static final double NEGATIVE_THRESHOLD = 0.3;

    /** 负面内容惩罚因子 */
    private static final double NEGATIVE_PENALTY = 0.7;

    /** 精华帖基础加分 */
    private static final double FEATURED_BONUS = 20.0;

    /**
     * 每 10 分钟全量刷新最近 14 天帖子的热度
     */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void recalculateHeatScores() {
        log.debug("开始执行 [帖子热度衰减] 定时任务...");
        long start = System.currentTimeMillis();

        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(ACTIVE_DAYS);
            LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Post::getStatus, 1)
                    .ge(Post::getCreateTime, cutoff);

            List<Post> posts = postMapper.selectList(wrapper);
            int updated = 0;

            for (Post post : posts) {
                double newHeat = calculateHeat(post);
                double oldHeat = post.getHeatScore() != null ? post.getHeatScore() : 0.0;

                // 只在热度变化超过 0.5 时才更新，减少不必要的写操作
                if (Math.abs(newHeat - oldHeat) >= 0.5) {
                    post.setHeatScore(Math.round(newHeat * 100.0) / 100.0);
                    postMapper.updateById(post);
                    updated++;
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            if (updated > 0) {
                log.info("[帖子热度衰减] 完成, 扫描 {} 篇, 更新 {} 篇, 耗时 {} ms",
                        posts.size(), updated, elapsed);
            }
        } catch (Exception e) {
            log.error("[帖子热度衰减] 执行失败", e);
        }
    }

    /**
     * 计算单篇帖子的热度值
     */
    public double calculateHeat(Post post) {
        // 1. 互动得分
        int views = post.getViewCount() != null ? post.getViewCount() : 0;
        int likes = post.getLikeCount() != null ? post.getLikeCount() : 0;
        int comments = post.getCommentCount() != null ? post.getCommentCount() : 0;
        int collects = post.getCollectCount() != null ? post.getCollectCount() : 0;

        double interactionScore = views * 0.5
                + likes * 3.0
                + comments * 5.0
                + collects * 2.0;

        // 2. 精华帖加分
        if (post.getIsFeatured() != null && post.getIsFeatured() == 1) {
            interactionScore += FEATURED_BONUS;
        }

        // 3. 时间衰减因子
        double ageInHours = 1.0; // 至少 1 小时，避免除零
        if (post.getCreateTime() != null) {
            Duration age = Duration.between(post.getCreateTime(), LocalDateTime.now());
            ageInHours = Math.max(1.0, age.toHours());
        }
        double timeFactor = Math.pow(1 + ageInHours / 24.0, GRAVITY);

        // 4. 基础热度
        double heat = interactionScore / timeFactor;

        // 5. 情感惩罚（低质量/负面内容降权）
        if (post.getSentimentScore() != null
                && post.getSentimentScore().doubleValue() < NEGATIVE_THRESHOLD) {
            heat *= NEGATIVE_PENALTY;
        }

        return Math.max(0, heat);
    }

    /**
     * 手动触发（供测试或管理接口调用）
     */
    public void triggerManually() {
        log.info("手动触发帖子热度衰减");
        recalculateHeatScores();
    }
}
