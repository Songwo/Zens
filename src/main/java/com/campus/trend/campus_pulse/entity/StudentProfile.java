package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_student_profile")
public class StudentProfile {
    @TableId
    private String userId;
    private String studentNo;
    private String realName;
    private String idCard;
    private String college;
    private String className;
    private LocalDate enrollmentDate;
    private Integer graduationStatus; // 0:在读 1:毕业 2:延毕
    private String phone;
    private String campus;
    private String dormBuilding;
    private String dormRoom;
    private String dormBed;
    private BigDecimal totalCredits;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
