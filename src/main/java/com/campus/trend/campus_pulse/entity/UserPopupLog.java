package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user_popup_log")
public class UserPopupLog {
    private String userId;
    private Long announcementId;
    private LocalDateTime showTime;
}
