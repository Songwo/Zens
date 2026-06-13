package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 投票选项表
 */
@Data
@Accessors(chain = true)
@TableName("poll_option")
public class PollOption implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属投票ID
     */
    private Long pollId;

    /**
     * 选项文本
     */
    private String optionText;

    /**
     * 展示顺序
     */
    private Integer optionOrder;

    /**
     * 该项票数（冗余缓存）
     */
    private Integer voteCount;
}
