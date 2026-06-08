package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Song：编辑评论请求体
 */
@Data
public class CommentEditReq {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容过长")
    private String content;
}
