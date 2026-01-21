package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("sys_course")
public class Course {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String courseCode;
    private String name;
    private String teacherName;
    private BigDecimal credits;
    private Integer maxCapacity;
    private Integer currentCapacity;
    private String classTime;
    private String location;
    private String semester;
    private Integer status;
}
