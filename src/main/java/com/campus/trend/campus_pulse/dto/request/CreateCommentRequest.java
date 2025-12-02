package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCommentRequest {

    @NotBlank(message = "帖子ID不能为空")
    private String postId;

    @NotBlank(message = "评论内容不能为空")
    private String content;

    /**
     * 父评论ID (0为根评论)
     */
    private String parentId = "0";

    /**
     * 被回复的人ID
     */
    private String replyUserId;

    /**
     * 是否匿名 1:是 0:否
     */
    private Integer isAnonymous = 0;
}
