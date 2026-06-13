package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShortLinkCommentReq {

    @NotBlank(message = "帖子不存在")
    private String postId;

    @NotBlank(message = "评论不存在")
    private String commentId;
}
