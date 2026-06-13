package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.entity.CommentCollect;
import com.campus.trend.campus_pulse.mapper.CommentCollectMapper;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.service.CommentCollectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CommentCollectServiceImpl extends ServiceImpl<CommentCollectMapper, CommentCollect>
        implements CommentCollectService {

    private final CommentMapper commentMapper;

    @Override
    public boolean isCollected(String commentId, String userId) {
        Long count = lambdaQuery()
                .eq(CommentCollect::getCommentId, commentId)
                .eq(CommentCollect::getUserId, userId)
                .count();
        return count != null && count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleCollect(String commentId, String userId) {
        if (isCollected(commentId, userId)) {
            boolean removed = lambdaUpdate()
                    .eq(CommentCollect::getCommentId, commentId)
                    .eq(CommentCollect::getUserId, userId)
                    .remove();
            if (removed) {
                Comment comment = commentMapper.selectById(commentId);
                if (comment != null) {
                    int currentCount = comment.getCollectCount() != null ? comment.getCollectCount() : 0;
                    comment.setCollectCount(Math.max(0, currentCount - 1));
                    commentMapper.updateById(comment);
                }
            }
            return false;
        }

        CommentCollect collect = new CommentCollect()
                .setCommentId(commentId)
                .setUserId(userId)
                .setCreatedAt(LocalDateTime.now());
        boolean saved = save(collect);
        if (!saved) {
            return false;
        }
        Comment comment = commentMapper.selectById(commentId);
        if (comment != null) {
            int currentCount = comment.getCollectCount() != null ? comment.getCollectCount() : 0;
            comment.setCollectCount(currentCount + 1);
            commentMapper.updateById(comment);
        }
        return true;
    }
}
