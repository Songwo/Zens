package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.trend.campus_pulse.entity.CommentLike;
import com.campus.trend.campus_pulse.mapper.CommentLikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 一次性迁移：把旧版存于 Redis 的评论点赞状态（comment:like:{commentId}:{userId} 键）
 * 落库到 comment_likes 表。评论点赞已改为 DB 持久化，本 runner 仅用于存量数据补齐。
 *
 * 默认关闭；部署新版本后配置 campus.migration.comment-like-redis-to-db=true 启动一次，
 * 确认日志迁移计数无误后移除该配置。可重复执行（唯一约束幂等跳过已存在记录）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.migration.comment-like-redis-to-db", havingValue = "true")
public class CommentLikeRedisMigrationRunner implements CommandLineRunner {

    private static final String LEGACY_KEY_PATTERN = "comment:like:*";
    private static final String LEGACY_KEY_PREFIX = "comment:like:";

    private final StringRedisTemplate stringRedisTemplate;
    private final CommentLikeMapper commentLikeMapper;

    @Override
    public void run(String... args) {
        log.info("[迁移] 开始扫描 Redis 评论点赞键 ({}) 迁移至 comment_likes 表...", LEGACY_KEY_PATTERN);
        long migrated = 0;
        long skipped = 0;
        long malformed = 0;

        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(LEGACY_KEY_PATTERN).count(500).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                // 键格式：comment:like:{commentId}:{userId}，commentId/userId 本身不含冒号
                String payload = key.substring(LEGACY_KEY_PREFIX.length());
                int splitIdx = payload.lastIndexOf(':');
                if (splitIdx <= 0 || splitIdx >= payload.length() - 1) {
                    malformed++;
                    continue;
                }
                String commentId = payload.substring(0, splitIdx);
                String userId = payload.substring(splitIdx + 1);
                if (!StringUtils.hasText(commentId) || !StringUtils.hasText(userId)) {
                    malformed++;
                    continue;
                }

                Long exists = commentLikeMapper.selectCount(Wrappers.<CommentLike>lambdaQuery()
                        .eq(CommentLike::getCommentId, commentId)
                        .eq(CommentLike::getUserId, userId));
                if (exists != null && exists > 0) {
                    skipped++;
                    continue;
                }
                try {
                    commentLikeMapper.insert(new CommentLike()
                            .setCommentId(commentId)
                            .setUserId(userId)
                            .setCreatedAt(LocalDateTime.now()));
                    migrated++;
                } catch (DuplicateKeyException e) {
                    skipped++;
                }
            }
        } catch (Exception e) {
            log.error("[迁移] Redis 评论点赞迁移中断: 已迁移={}, 已跳过={}, err={}", migrated, skipped, e.getMessage(), e);
            return;
        }

        log.info("[迁移] Redis 评论点赞迁移完成: 新写入={}, 已存在跳过={}, 非法键={}。"
                + "确认无误后请移除配置 campus.migration.comment-like-redis-to-db", migrated, skipped, malformed);
    }
}
