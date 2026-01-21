package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sys_grade")
public class Grade {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Long courseId;
    private BigDecimal score;
    private String semester;
    private Integer isPassed;
    private LocalDateTime createTime;
}
