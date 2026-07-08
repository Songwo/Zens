package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.ShortLinkResolveResp;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.ShortLink;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.ShortLinkMapper;
import com.campus.trend.campus_pulse.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLink> implements ShortLinkService {

    private static final String TARGET_COMMENT = "comment";
    private static final String AUDIT_STATUS_DELETED = "DELETED";
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";
    private static final int POST_STATUS_PUBLISHED = 1;
    private static final char[] CODE_CHARS = "23456789abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createCommentShortLink(String postId, String commentId, String creatorId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || !StringUtils.hasText(comment.getPostId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "评论不存在");
        }
        if (!comment.getPostId().equals(postId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "评论与帖子不匹配");
        }
        if (AUDIT_STATUS_DELETED.equalsIgnoreCase(comment.getAuditStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "已删除评论无法生成分享链接");
        }

        Post post = postMapper.selectById(postId);
        if (!isPublicPost(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该帖子当前不可分享");
        }

        ShortLink existing = lambdaQuery()
                .eq(ShortLink::getTargetType, TARGET_COMMENT)
                .eq(ShortLink::getCommentId, commentId)
                .last("LIMIT 1")
                .one();
        if (existing != null && StringUtils.hasText(existing.getCode())) {
            return existing.getCode();
        }

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 8; i++) {
            String code = generateCode();
            if (getById(code) != null) {
                continue;
            }
            ShortLink link = new ShortLink();
            link.setCode(code);
            link.setTargetType(TARGET_COMMENT);
            link.setPostId(postId);
            link.setCommentId(commentId);
            link.setCreatorId(StringUtils.hasText(creatorId) ? creatorId : null);
            link.setCreateTime(now);
            link.setUpdateTime(now);
            save(link);
            return code;
        }

        throw new BusinessException(ResultCode.FAILED, "短链接生成失败，请稍后重试");
    }

    @Override
    public ShortLinkResolveResp resolve(String code) {
        if (!StringUtils.hasText(code) || !code.matches("^[0-9A-Za-z]{6,24}$")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "短链接不存在");
        }
        ShortLink link = getById(code.trim());
        if (link == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "短链接不存在");
        }

        ShortLinkResolveResp resp = new ShortLinkResolveResp();
        BeanUtils.copyProperties(link, resp);
        return resp;
    }

    private boolean isPublicPost(Post post) {
        return post != null
                && Integer.valueOf(POST_STATUS_PUBLISHED).equals(post.getStatus())
                && !AUDIT_STATUS_DELETED.equalsIgnoreCase(post.getAuditStatus())
                && (!StringUtils.hasText(post.getAuditStatus())
                || AUDIT_STATUS_APPROVED.equalsIgnoreCase(post.getAuditStatus()));
    }

    private String generateCode() {
        char[] code = new char[10];
        for (int i = 0; i < code.length; i++) {
            code[i] = CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)];
        }
        return new String(code);
    }
}
