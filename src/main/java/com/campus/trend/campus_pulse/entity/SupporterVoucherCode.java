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
@TableName("supporter_voucher_code")
public class SupporterVoucherCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer quota;
    private String codeCiphertext;
    private String codeHash;
    private String status;
    private Long assignedGrantId;
    private String importedBy;
    private LocalDateTime importedAt;
    private LocalDateTime assignedAt;
}
