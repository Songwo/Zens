package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 投票选项响应：含票数、占比、当前用户是否选了此项。
 */
@Data
@Accessors(chain = true)
public class PollOptionResp implements Serializable {

    private Long id;

    private String optionText;

    private Integer optionOrder;

    /**
     * 该项票数
     */
    private Integer voteCount;

    /**
     * 占总票数的百分比（0-100，保留1位小数；总票数为0时为0）
     */
    private Double percent;

    /**
     * 当前用户是否选择了此项
     */
    private Boolean votedByMe;
}
