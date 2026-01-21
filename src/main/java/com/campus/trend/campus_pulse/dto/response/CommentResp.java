package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentResp {
    private String id;
    private String postId;
    private String userId;
    private String nickname;
    private String userAvatar;
    private String content;
    private String parentId;
    private String replyUserId;
    private String replyUserNickname; // New: nickname of the person being replied to
    private Integer isAnonymous;
    private Integer likeCount;
    private LocalDateTime createTime;
    private Boolean isLiked; // New: current user liked status
    private List<CommentResp> children;
}
