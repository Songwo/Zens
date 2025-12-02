package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.request.CreateCommentRequest;
import com.campus.trend.campus_pulse.entity.SysComment;

import java.util.List;

public interface CommentService extends IService<SysComment> {

    void addComment(CreateCommentRequest request, String userId);

    void deleteComment(String commentId, String userId);

    List<SysComment> getCommentsByPostId(String postId);
}
