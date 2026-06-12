package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 投票响应：投票全貌 + 当前用户的可投状态。
 * 单独接口返回，不进入帖子详情缓存（票数频繁变动）。
 */
@Data
@Accessors(chain = true)
public class PollResp implements Serializable {

    private Long id;

    private String postId;

    /**
     * 投票问题，空则前端用帖子标题兜底
     */
    private String title;

    /**
     * 0单选 1多选
     */
    private Integer multiChoice;

    /**
     * 多选时最多可选项数；单选恒为1；0=不限
     */
    private Integer maxChoices;

    /**
     * 截止时间，空=不限期
     */
    private LocalDateTime deadline;

    /**
     * 1进行中 0已关闭
     */
    private Integer status;

    /**
     * 参与投票的去重人数
     */
    private Integer voterCount;

    /**
     * 总票数（多选时 = 各选项票数之和，可大于 voterCount）
     */
    private Integer totalVotes;

    private String createdBy;

    private LocalDateTime createdAt;

    /**
     * 选项列表（按 optionOrder 升序）
     */
    private List<PollOptionResp> options;

    // ============ 当前用户视角的状态（便于前端直接渲染，无需二次判断）============

    /**
     * 是否已截止（deadline 已过 或 status=0）
     */
    private Boolean closed;

    /**
     * 当前用户是否已投票
     */
    private Boolean votedByMe;

    /**
     * 当前用户能否投票（已登录 且 未截止 且 未投过）
     */
    private Boolean canVote;

    /**
     * 是否应展示结果（已投票 或 已截止 或 是创建者）
     */
    private Boolean showResult;
}
