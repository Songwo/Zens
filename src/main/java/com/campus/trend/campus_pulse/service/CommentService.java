package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.request.CommentCreateReq;
import com.campus.trend.campus_pulse.entity.Comment;


public interface CommentService extends IService<Comment> {

    void addComment(CommentCreateReq request, String userId);

    void deleteComment(String commentId, String userId);

    /** Song：编辑评论内容（仅评论作者、管理员或对应板块版主） */
    void editComment(String commentId, String content, String userId);

    void restoreComment(String commentId, String userId);

    com.baomidou.mybatisplus.core.metadata.IPage<com.campus.trend.campus_pulse.dto.response.CommentResp> getCommentsByPostId(
            String postId, Integer page, Integer size);

    void toggleLike(String commentId, String userId);

    boolean toggleCollect(String commentId, String userId);
}
