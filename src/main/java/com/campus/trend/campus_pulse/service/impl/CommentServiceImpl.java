package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.request.CreateCommentRequest;
import com.campus.trend.campus_pulse.entity.SysComment;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.mapper.SysCommentMapper;
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import com.campus.trend.campus_pulse.service.CommentService;
import com.campus.trend.campus_pulse.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<SysCommentMapper, SysComment> implements CommentService {

    private final SysPostMapper sysPostMapper;
    private final UserProfileService userProfileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addComment(CreateCommentRequest request, String userId) {
        SysComment comment = new SysComment();
        comment.setPostId(request.getPostId());
        comment.setUserId(userId);
        comment.setContent(request.getContent());
        comment.setParentId(request.getParentId());
        comment.setReplyUserId(request.getReplyUserId());
        comment.setIsAnonymous(request.getIsAnonymous());
        comment.setCreateTime(LocalDateTime.now());
        comment.setLikeCount(0);
        this.save(comment);

        // 更新帖子评论数
        SysPost post = sysPostMapper.selectById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(post.getCommentCount() + 1);
            sysPostMapper.updateById(post);
        }

        // 更新用户画像
        userProfileService.addContribution(userId, 2); // 评论贡献值+2
        userProfileService.updateLastActiveTime(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(String commentId, String userId) {
        SysComment comment = getById(commentId);
        if (comment == null) {
            throw new RuntimeException("评论不存在");
        }

        // TODO: 只有评论作者可以删除 (或者管理员，只校验作者)
        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该评论");
        }

        this.removeById(commentId);

        // 更新帖子评论数
        SysPost post = sysPostMapper.selectById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
            sysPostMapper.updateById(post);
        }
    }

    @Override
    public List<SysComment> getCommentsByPostId(String postId) {
        List<SysComment> sysCommentList = this.list(Wrappers.<SysComment>lambdaQuery()
                .eq(SysComment::getPostId, postId)
                .orderByAsc(SysComment::getCreateTime));

        // 过滤出父ID为 "0" 的根节点，开始递归构建
        return sysCommentList.stream()
                .filter(comment -> "0".equals(comment.getParentId()))
                .peek(comment -> comment.setChildren(getChildrens(comment, sysCommentList)))
                .collect(Collectors.toList());
    }

    /**
     * 递归查找子节点
     */
    private List<SysComment> getChildrens(SysComment root, List<SysComment> all) {
        return all.stream()
                // 筛选出所有 parentId 等于当前 root id 的数据
                .filter(comment -> Objects.equals(comment.getParentId(), root.getId()))
                .peek(comment -> {
                    // 递归：继续去找当前子节点的子节点
                    comment.setChildren(getChildrens(comment, all));
                })
                .collect(Collectors.toList());
    }
}
