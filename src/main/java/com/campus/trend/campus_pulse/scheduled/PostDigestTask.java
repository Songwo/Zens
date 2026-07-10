package com.campus.trend.campus_pulse.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostSubscription;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.PostSubscriptionMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 定时任务：帖子追踪每日邮件摘要（digest）。
 * 聚合过去 24h 内被追踪帖的新评论，按用户合并成一封 HTML 邮件。
 * 仅对 emailNotifyEnabled=1 且有邮箱的用户发送；统计时排除用户自己发的评论。
 * 站内通知与本任务无关（评论时已实时下发）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PostDigestTask {

    private final PostSubscriptionMapper postSubscriptionMapper;
    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final MailService mailService;

    @Value("${campus.site.url:https://www.allinsong.top}")
    private String siteUrl;

    /**
     * 默认每天早上 8 点执行；可用 campus.digest.cron 覆盖。
     */
    @Scheduled(cron = "${campus.digest.cron:0 0 8 * * ?}")
    public void sendDailyDigest() {
        log.info("[定时任务] 帖子追踪邮件摘要开始...");
        try {
            LocalDateTime windowStart = LocalDateTime.now().minusHours(24);

            // 1. 过去 24h 的新评论（只算正常状态，软删除的不计）
            List<Comment> recentComments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                    .ge(Comment::getCreateTime, windowStart)
                    .ne(Comment::getAuditStatus, "DELETED"));
            if (recentComments.isEmpty()) {
                log.info("[定时任务] 24h 内无新评论，摘要结束。");
                return;
            }

            // postId -> 该帖窗口内评论列表
            Map<String, List<Comment>> commentsByPost = recentComments.stream()
                    .filter(c -> StringUtils.hasText(c.getPostId()))
                    .collect(Collectors.groupingBy(Comment::getPostId));

            // 2. 这些帖子的订阅关系
            List<PostSubscription> subs = postSubscriptionMapper.selectList(new LambdaQueryWrapper<PostSubscription>()
                    .in(PostSubscription::getPostId, commentsByPost.keySet()));
            if (subs.isEmpty()) {
                log.info("[定时任务] 活跃帖子无订阅者，摘要结束。");
                return;
            }

            // 3. 聚合: userId -> (postId -> 排除其本人评论后的新评论数)
            Map<String, Map<String, Integer>> digestByUser = new HashMap<>();
            for (PostSubscription sub : subs) {
                List<Comment> postComments = commentsByPost.get(sub.getPostId());
                if (postComments == null) continue;
                int count = (int) postComments.stream()
                        .filter(c -> !Objects.equals(c.getUserId(), sub.getUserId()))
                        .count();
                if (count <= 0) continue; // 该帖新评论全是订阅者自己发的，不提醒
                digestByUser.computeIfAbsent(sub.getUserId(), k -> new HashMap<>())
                        .put(sub.getPostId(), count);
            }
            if (digestByUser.isEmpty()) {
                log.info("[定时任务] 聚合后无可提醒用户，摘要结束。");
                return;
            }

            // 4. 帖子标题（一次批量查）
            Map<String, Post> postMap = postMapper.selectBatchIds(
                            digestByUser.values().stream()
                                    .flatMap(m -> m.keySet().stream())
                                    .collect(Collectors.toSet()))
                    .stream().collect(Collectors.toMap(Post::getId, p -> p));

            // 5. 逐用户过滤邮件开关并发送（单用户失败不影响其他人）
            List<User> users = userMapper.selectBatchIds(digestByUser.keySet());
            int sent = 0;
            for (User user : users) {
                try {
                    if (user.getEmailNotifyEnabled() == null || user.getEmailNotifyEnabled() != 1) continue;
                    if (!StringUtils.hasText(user.getEmail())) continue;

                    Map<String, Integer> postsForUser = digestByUser.get(user.getId());
                    if (postsForUser == null || postsForUser.isEmpty()) continue;

                    String html = buildDigestHtml(user, postsForUser, postMap);
                    String subject = "【Zens社区】你追踪的 " + postsForUser.size() + " 个主题有新回复";
                    mailService.sendHtmlMail(user.getEmail(), subject, html);
                    sent++;
                } catch (Exception e) {
                    log.warn("[定时任务] 用户摘要邮件发送失败: userId={}, err={}", user.getId(), e.getMessage());
                }
            }
            log.info("[定时任务] 帖子追踪邮件摘要完成：候选 {} 人，实发 {} 封。", digestByUser.size(), sent);
        } catch (Exception e) {
            log.error("[定时任务] 帖子追踪邮件摘要执行失败", e);
        }
    }

    private String buildDigestHtml(User user, Map<String, Integer> postsForUser, Map<String, Post> postMap) {
        String nickname = StringUtils.hasText(user.getNickname()) ? user.getNickname() : "同学";
        StringBuilder items = new StringBuilder();
        postsForUser.forEach((postId, count) -> {
            Post post = postMap.get(postId);
            String title = post != null && StringUtils.hasText(post.getTitle()) ? post.getTitle() : "（帖子已不可见）";
            String link = siteUrl + "/t/" + postId;
            items.append("""
                    <tr>
                        <td style="padding: 12px 0; border-bottom: 1px solid #f0f0f0;">
                            <a href="%s" style="color: #1a1a1a; text-decoration: none; font-size: 15px; font-weight: 600;">%s</a>
                            <div style="margin-top: 4px; color: #8a8a8a; font-size: 13px;">%d 条新回复</div>
                        </td>
                    </tr>
                    """.formatted(link, HtmlUtils.htmlEscape(title), count));
        });

        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin: 0; padding: 0; background-color: #f7f9fc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background-color: #f7f9fc; padding: 40px 0;">
                        <tr><td align="center">
                            <table width="560" cellpadding="0" cellspacing="0" style="background: #ffffff; border-radius: 12px; padding: 36px 40px; text-align: left;">
                                <tr><td>
                                    <div style="font-size: 18px; font-weight: 700; color: #1a1a1a;">Zens · 追踪摘要</div>
                                    <div style="margin-top: 14px; color: #4a4a4a; font-size: 14px; line-height: 1.7;">
                                        %s，你好！过去 24 小时内，你追踪的主题有了新动态：
                                    </div>
                                    <table width="100%%" cellpadding="0" cellspacing="0" style="margin-top: 12px;">
                                        %s
                                    </table>
                                    <div style="margin-top: 20px; color: #a0a0a0; font-size: 12px; line-height: 1.6;">
                                        不想再收到此类邮件？可在帖子页取消追踪，或到「设置」中关闭邮件通知。
                                    </div>
                                </td></tr>
                            </table>
                        </td></tr>
                    </table>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(nickname), items.toString());
    }
}
