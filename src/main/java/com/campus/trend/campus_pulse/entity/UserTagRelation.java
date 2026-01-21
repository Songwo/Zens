package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("sys_user_tag_relation")
public class UserTagRelation implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String userId;

    private Long tagId;

    /**
     * 兴趣权重 (1.0 - 5.0)
     */
    private BigDecimal score;

    private LocalDateTime createTime;
}
