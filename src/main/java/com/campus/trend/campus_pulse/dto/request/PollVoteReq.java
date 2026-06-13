package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 投票请求：对某个投票选择 1..N 个选项。
 */
@Getter
@Setter
public class PollVoteReq {

    @NotNull(message = "投票ID不允许为空")
    private Long pollId;

    /**
     * 所选选项ID列表。单选传1个；多选传多个，服务层校验数量与归属。
     */
    @NotEmpty(message = "请至少选择一个选项")
    private List<Long> optionIds;
}
