package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_leave_request")
public class SysLeaveRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Integer type; // 1:事假 2:病假
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String reason;
    private Integer status; // 0:待审批 1:已通过 2:已驳回
    private String approverId;
    private LocalDateTime approveTime;
    private LocalDateTime createTime;
}
