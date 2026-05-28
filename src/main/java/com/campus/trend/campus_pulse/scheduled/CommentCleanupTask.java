package com.campus.trend.campus_pulse.scheduled;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定时任务：物理清理软删超过 3 天的评论。
 * 与 PostCleanupTask 错开 30 分钟执行，避免对同一帖子的并发写竞争。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommentCleanupTask {

    private final CommentMapper commentMapper;

    /**
     * 每天 04:30 执行
     */
    @Scheduled(cron = "0 30 4 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredDeletedComments() {
        log.info("[定时任务] 开始扫描软删除超过 3 天的评论...");
        LocalDateTime threshold = LocalDateTime.now().minusDays(3);

        List<Comment> expired = commentMapper.selectList(
                Wrappers.<Comment>lambdaQuery()
                        .eq(Comment::getAuditStatus, "DELETED")
                        .le(Comment::getUpdateTime, threshold)
        );
        if (expired.isEmpty()) {
            log.info("[定时任务] 无需清理的过期评论");
            return;
        }

        List<String> ids = expired.stream().map(Comment::getId).collect(Collectors.toList());
        commentMapper.deleteBatchIds(ids);
        log.info("[定时任务] 物理删除 {} 条过期评论", ids.size());
    }
}
