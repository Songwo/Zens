package com.campus.trend.campus_pulse.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.AnswerAdoption;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.entity.Poll;
import com.campus.trend.campus_pulse.entity.PollOption;
import com.campus.trend.campus_pulse.entity.PollVote;
import com.campus.trend.campus_pulse.entity.PostCollect;
import com.campus.trend.campus_pulse.entity.PostLike;
import com.campus.trend.campus_pulse.entity.PostSubscription;
import com.campus.trend.campus_pulse.entity.PostVersionHistory;
import com.campus.trend.campus_pulse.entity.ViewLog;
import com.campus.trend.campus_pulse.mapper.AnswerAdoptionMapper;
import com.campus.trend.campus_pulse.mapper.PollMapper;
import com.campus.trend.campus_pulse.mapper.PollOptionMapper;
import com.campus.trend.campus_pulse.mapper.PollVoteMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.PostSubscriptionMapper;
import com.campus.trend.campus_pulse.mapper.PostVersionHistoryMapper;
import com.campus.trend.campus_pulse.service.CommentService;
import com.campus.trend.campus_pulse.service.PostCollectService;
import com.campus.trend.campus_pulse.service.PostLikeService;
import com.campus.trend.campus_pulse.service.PostMediaService;
import com.campus.trend.campus_pulse.service.ViewLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务：清理软删除超过 7 天的已过期帖子。
 * 级联硬删除主帖及所有关联的子评论、媒体文件、点赞、收藏与浏览日志，保证数据存储空间的优雅回收。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PostCleanupTask {

    private final PostMapper postMapper;
    private final CommentService commentService;
    private final PostLikeService postLikeService;
    private final PostCollectService postCollectService;
    private final PostMediaService postMediaService;
    private final ViewLogService viewLogService;
    private final PollMapper pollMapper;
    private final PollOptionMapper pollOptionMapper;
    private final PollVoteMapper pollVoteMapper;
    private final PostSubscriptionMapper postSubscriptionMapper;
    private final AnswerAdoptionMapper answerAdoptionMapper;
    private final PostVersionHistoryMapper postVersionHistoryMapper;

    /**
     * 每天凌晨 4 点执行清理任务
     */
    @Scheduled(cron = "0 0 4 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredDeletedPosts() {
        log.info("[定时任务] 开始扫描软删除超过 7 天的已过期帖子...");
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);

        // 检索 auditStatus 为 "DELETED" 且最后 updateTime 在 7 天前的帖子
        LambdaQueryWrapper<Post> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(Post::getAuditStatus, "DELETED")
                .le(Post::getUpdateTime, threshold);

        List<Post> expiredPosts = postMapper.selectList(queryWrapper);
        if (expiredPosts.isEmpty()) {
            log.info("[定时任务] 未发现需要清理的已过期帖子。");
            return;
        }

        log.info("[定时任务] 发现 {} 个已过期帖子，开始彻底硬删除关联的业务数据...", expiredPosts.size());

        for (Post post : expiredPosts) {
            String postId = post.getId();
            try {
                // 1. 删除文章评论
                commentService.remove(Wrappers.<Comment>lambdaQuery().eq(Comment::getPostId, postId));

                // 2. 删除文章点赞记录
                postLikeService.remove(Wrappers.<PostLike>lambdaQuery().eq(PostLike::getPostId, postId));

                // 3. 删除文章收藏记录
                postCollectService.remove(Wrappers.<PostCollect>lambdaQuery().eq(PostCollect::getPostId, postId));

                // 4. 彻底删除文章媒体数据
                postMediaService.deleteByPostId(postId);

                // 5. 删除浏览日志
                viewLogService.remove(Wrappers.<ViewLog>lambdaQuery().eq(ViewLog::getPostId, postId));

                // 6. 级联删除投票（poll → poll_option / poll_vote）
                Poll poll = pollMapper.selectOne(Wrappers.<Poll>lambdaQuery().eq(Poll::getPostId, postId));
                if (poll != null && poll.getId() != null) {
                    pollVoteMapper.delete(Wrappers.<PollVote>lambdaQuery().eq(PollVote::getPollId, poll.getId()));
                    pollOptionMapper.delete(Wrappers.<PollOption>lambdaQuery().eq(PollOption::getPollId, poll.getId()));
                    pollMapper.deleteById(poll.getId());
                }

                // 7. 删除主题订阅、采纳记录、版本历史
                postSubscriptionMapper.delete(Wrappers.<PostSubscription>lambdaQuery().eq(PostSubscription::getPostId, postId));
                answerAdoptionMapper.delete(Wrappers.<AnswerAdoption>lambdaQuery().eq(AnswerAdoption::getPostId, postId));
                postVersionHistoryMapper.delete(Wrappers.<PostVersionHistory>lambdaQuery().eq(PostVersionHistory::getPostId, postId));

                // 8. 删除帖子记录本身
                postMapper.deleteById(postId);

                log.info("[定时任务] 帖子 {} 及其级联数据已被成功永久硬删除。", postId);
            } catch (Exception e) {
                log.error("[定时任务] 帖子 {} 清理失败: ", postId, e);
            }
        }
        log.info("[定时任务] 已过期软删除帖子清理工作全部完成。");
    }
}
