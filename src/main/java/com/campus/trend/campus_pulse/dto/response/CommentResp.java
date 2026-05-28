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
    private String replyUserNickname; // Song：说明
    private Integer isAnonymous;
    private List<String> roles; // Song：用户角色列表
    private Integer likeCount;
    private String auditStatus;
    private LocalDateTime createTime;
    private Boolean isLiked; // Song：说明
    private List<CommentResp> children;
}
