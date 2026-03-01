package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TagsExtractReq {

    @NotBlank(message = "文章内容不允许为空")
    private String content;

    private int tagSize = 5;

    private int summarySize = 150;

}
