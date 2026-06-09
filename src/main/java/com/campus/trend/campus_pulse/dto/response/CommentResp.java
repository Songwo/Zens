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
    private String userBadgeText;
    private String userBadgeColor;
    private String userBadgeStyle;
    private String content;
    private String parentId;
    private String replyUserId;
    private String replyUserNickname; // Song：说明
    private Integer isAnonymous;
    private List<String> roles; // Song：用户角色列表
    private Integer likeCount;
    private Integer collectCount;
    private Integer isAdopted;
    private String auditStatus;
    private LocalDateTime createTime;
    private LocalDateTime editTime; // Song：最后编辑时间(为空表示未编辑过)
    private Boolean isLiked; // Song：说明
    private Boolean isCollected;
    private List<CommentResp> children;
}
