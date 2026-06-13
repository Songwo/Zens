package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发帖时附带的投票创建请求（嵌入 PostCreateReq.poll，可选）。
 */
@Getter
@Setter
public class PollCreateReq {

    /**
     * 投票问题，可空（前端用帖子标题兜底）
     */
    @Size(max = 200, message = "投票问题不超过200个字符")
    private String title;

    /**
     * 0单选 1多选，默认单选
     */
    private Integer multiChoice;

    /**
     * 多选时最多可选项数；0=不限。单选忽略此值。
     */
    private Integer maxChoices;

    /**
     * 截止时间，空=不限期
     */
    private LocalDateTime deadline;

    /**
     * 选项文本列表（2~10 项，服务层再次校验并去空白/去重）
     */
    @NotEmpty(message = "投票至少需要2个选项")
    @Size(min = 2, max = 10, message = "投票选项需为2~10个")
    private List<String> options;
}
