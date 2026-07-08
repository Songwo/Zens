package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommunityQaAskReq {

    @NotBlank(message = "问题不能为空")
    @Size(max = 400, message = "问题长度不能超过 400 个字符")
    private String question;

    @Size(max = 500, message = "检索问题长度不能超过 500 个字符")
    private String retrievalQuery;

    @Size(max = 2000, message = "上下文长度不能超过 2000 个字符")
    private String conversationContext;

    @Positive(message = "板块 ID 必须大于 0")
    private Long sectionId;

    @Min(value = 1, message = "limit 不能小于 1")
    @Max(value = 20, message = "limit 不能大于 20")
    private Integer limit = 6;

    private Boolean includeComments = true;

    @Min(value = 0, message = "commentsPerPost 不能小于 0")
    @Max(value = 10, message = "commentsPerPost 不能大于 10")
    private Integer commentsPerPost = 2;
}
