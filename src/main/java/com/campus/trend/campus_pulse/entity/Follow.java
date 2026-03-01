package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Song：关注表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("follows")
public class Follow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Song：说明
     */
    private String followerId;

    /**
     * Song：说明
     */
    private String followeeId;

    /**
     * Song：创建时间
     */
    private LocalDateTime createdAt;
}
