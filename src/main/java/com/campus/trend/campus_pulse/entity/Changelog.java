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
 * Song：发展历程 / 版本日志表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_changelog")
public class Changelog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String version;

    private String title;

    private String content;

    /**
     * Song：路线图阶段编号，如 "01"
     */
    private String stageNo;

    /**
     * Song：路线图阶段标签，如 "已上线"、"建设中"、"下一阶段"
     */
    private String stageLabel;

    /**
     * Song：上线状态：released / building / planned
     */
    private String roadmapStatus;

    /**
     * Song：卡片底部高亮信息，如 "发帖/评论/标签"
     */
    private String highlights;

    /**
     * Song：后续在线升级或详情入口，当前仅作为扩展配置保留
     */
    private String actionPath;

    private Integer upgradeEnabled;

    private String upgradeUrl;

    /**
     * Song：时间标签，如 "2026-02-26"
     */
    private String timestamp;

    private Integer status;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
