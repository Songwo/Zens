package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 信任等级信息响应
 */
@Data
@Accessors(chain = true)
public class TrustInfoResp {

    /** 当前信任等级 0-4 */
    private Integer trustLevel;

    /** 等级标签（新人/基础/成员/常客/领袖） */
    private String levelLabel;

    /** 各级阈值展示（前端渲染进度用） */
    private List<LevelSpec> levels;

    /** 当前窗口内各维度实际指标 */
    private Metrics metrics;

    /** 是否处于禁言状态 */
    private Boolean silenced;

    /** 禁言截止时间 */
    private LocalDateTime silencedUntil;

    @Data
    @Accessors(chain = true)
    public static class LevelSpec {
        private int level;
        private String label;
        private String description;
        private List<String> privileges;
    }

    @Data
    @Accessors(chain = true)
    public static class Metrics {
        /** 注册天数 */
        private long daysSinceRegister;
        /** 累计访问不同日期数 */
        private int daysVisited;
        /** 最近 100 天访问不同日期数 */
        private int daysVisitedRecent;
        /** 最近 100 天进入不同帖子数 */
        private int postsEnteredRecent;
        /** 最近 100 天阅读帖子数 */
        private int postsReadRecent;
        /** 累计阅读秒数 */
        private long readTimeSec;
        /** 收到点赞数 */
        private int likesReceived;
        /** 给出点赞数 */
        private int likesGiven;
        /** 发帖数 */
        private int postsCreated;
    }
}
