package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_announcement")
public class SysAnnouncement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String type; // WELCOME/NOTICE
    private Integer isActive;
    private LocalDateTime createTime;
}
