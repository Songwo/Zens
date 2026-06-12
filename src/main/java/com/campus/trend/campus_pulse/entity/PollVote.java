package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投票记录表（唯一约束 poll_id+user_id+option_id 兜底并发与重复投票）
 */
@Data
@Accessors(chain = true)
@TableName("poll_vote")
public class PollVote implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long pollId;

    private Long optionId;

    private String userId;

    private LocalDateTime createdAt;
}
