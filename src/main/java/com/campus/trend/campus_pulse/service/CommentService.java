package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.request.CommentCreateReq;
import com.campus.trend.campus_pulse.entity.Comment;


public interface CommentService extends IService<Comment> {

    void addComment(CommentCreateReq request, String userId);

    void deleteComment(String commentId, String userId);

    com.baomidou.mybatisplus.core.metadata.IPage<com.campus.trend.campus_pulse.dto.response.CommentResp> getCommentsByPostId(
            String postId, Integer page, Integer size);

    void toggleLike(String commentId, String userId);
}
