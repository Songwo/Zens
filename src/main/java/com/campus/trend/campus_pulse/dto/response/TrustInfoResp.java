package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /** 快照生成时间，前端据此展示“刚刚更新”而不是把旧数据当实时数据。 */
    private LocalDateTime asOf;

    /** 行为滚动窗口天数。 */
    private Integer windowDays;

    /** LIVE 表示来自行为明细实时聚合；DEGRADED 表示聚合链路异常。 */
    private String dataStatus;

    /** 到下一级的逐项进度；最高等级时为空。 */
    private List<MetricProgress> metricProgress;

    /** 所有必需指标进度的平均值。 */
    private Integer overallProgress;

    @Data
    @Accessors(chain = true)
    public static class LevelSpec {
        private int level;
        private String label;
        private String description;
        private List<String> privileges;
        private Map<String, Long> requirements;
    }

    @Data
    @Accessors(chain = true)
    public static class MetricProgress {
        private String key;
        private String label;
        private long current;
        private long target;
        private String unit;
        private int percent;
        private boolean met;
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
