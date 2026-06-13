package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.entity.*;
import com.campus.trend.campus_pulse.mapper.AnswerAdoptionMapper;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.AnswerAdoptionService;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.PostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 答案采纳服务实现
 */
@Slf4j
@Service
public class AnswerAdoptionServiceImpl extends ServiceImpl<AnswerAdoptionMapper, AnswerAdoption> implements AnswerAdoptionService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SectionMapper sectionMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PostService postService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adoptAnswer(String postId, String commentId, String userId) {
        // 1. 验证帖子存在且是作者本人
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException("只有帖子作者可以采纳答案");
        }

        // 1.1 验证板块是否支持答案采纳（仅"答疑解惑"等问答板块开放）
        Section section = sectionMapper.selectById(post.getSectionId());
        if (section == null || section.getAllowAdoption() == null || section.getAllowAdoption() != 1) {
            throw new BusinessException("当前板块不支持答案采纳，请在「答疑解惑」板块发帖");
        }

        // 2. 验证评论存在且属于该帖子
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || !comment.getPostId().equals(postId)) {
            throw new BusinessException("评论不存在或不属于该帖子");
        }
        if (comment.getUserId().equals(userId)) {
            throw new BusinessException("不能采纳自己的回答");
        }

        // 3. 检查是否已有采纳答案
        AnswerAdoption existing = this.getAdoptedAnswer(postId);
        if (existing != null) {
            throw new BusinessException("该帖子已有采纳答案，请先取消后再采纳");
        }

        // 4. 创建采纳记录
        AnswerAdoption adoption = new AnswerAdoption()
                .setPostId(postId)
                .setCommentId(commentId)
                .setAdoptedBy(userId)
                .setAdoptedAt(LocalDateTime.now())
                .setReputationGranted(15)
                .setExpGranted(20);
        this.save(adoption);

        // 5. 更新帖子标记
        post.setHasAdoptedAnswer(1);
        postMapper.updateById(post);

        // 6. 更新评论标记
        comment.setIsAdopted(1);
        commentMapper.updateById(comment);

        postService.refreshPostCaches(postId);

        // 7. 给回答者增加声望和经验
        User commentAuthor = userMapper.selectById(comment.getUserId());
        if (commentAuthor != null) {
            commentAuthor.setReputation(commentAuthor.getReputation() + 15);
            commentAuthor.setExperience(commentAuthor.getExperience() + 20);
            userMapper.updateById(commentAuthor);
        }

        // 8. 发送通知
        try {
            notificationService.createNotification(
                    comment.getUserId(),
                    "answer_adopted",
                    "你的回答被采纳",
                    "你在帖子「" + post.getTitle() + "」中的回答被作者采纳为最佳答案，获得 +15 声望 +20 经验",
                    postId,
                    userId
            );
        } catch (Exception e) {
            log.error("发送采纳通知失败", e);
        }

        log.info("答案采纳成功: postId={}, commentId={}, userId={}", postId, commentId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAdoption(String postId, String userId) {
        // 1. 验证帖子存在且是作者本人或管理员
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }

        User user = userMapper.selectById(userId);
        boolean isAuthor = post.getUserId().equals(userId);
        boolean isAdmin = user != null && (user.getRole().contains("ADMIN") || user.getRole().contains("MODERATOR"));

        if (!isAuthor && !isAdmin) {
            throw new BusinessException("只有帖子作者或管理员可以取消采纳");
        }

        // 2. 查找采纳记录
        AnswerAdoption adoption = this.getAdoptedAnswer(postId);
        if (adoption == null) {
            throw new BusinessException("该帖子没有采纳答案");
        }

        // 3. 删除采纳记录
        this.removeById(adoption.getId());

        // 4. 更新帖子标记
        post.setHasAdoptedAnswer(0);
        postMapper.updateById(post);

        // 5. 更新评论标记
        Comment comment = commentMapper.selectById(adoption.getCommentId());
        if (comment != null) {
            comment.setIsAdopted(0);
            commentMapper.updateById(comment);

            // 6. 扣除回答者的声望和经验
            User commentAuthor = userMapper.selectById(comment.getUserId());
            if (commentAuthor != null) {
                commentAuthor.setReputation(Math.max(0, commentAuthor.getReputation() - 15));
                commentAuthor.setExperience(Math.max(0, commentAuthor.getExperience() - 20));
                userMapper.updateById(commentAuthor);
            }
        }

        postService.refreshPostCaches(postId);

        log.info("取消采纳成功: postId={}, userId={}", postId, userId);
    }

    @Override
    public AnswerAdoption getAdoptedAnswer(String postId) {
        LambdaQueryWrapper<AnswerAdoption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnswerAdoption::getPostId, postId);
        return this.getOne(wrapper);
    }
}
