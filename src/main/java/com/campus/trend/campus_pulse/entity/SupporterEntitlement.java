package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("supporter_entitlement")
public class SupporterEntitlement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String planCode;
    private String planNameSnapshot;
    private String sourceOrderNo;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private String status;
    private LocalDateTime createdAt;
}
