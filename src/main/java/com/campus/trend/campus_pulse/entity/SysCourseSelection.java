package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_course_selection")
public class SysCourseSelection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Long courseId;
    private LocalDateTime createTime;
}
