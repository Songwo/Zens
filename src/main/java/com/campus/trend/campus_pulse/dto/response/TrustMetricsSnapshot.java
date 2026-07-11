package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

/**
 * 从行为明细表实时聚合出的权威快照，不依赖 sys_user 中可能漂移的冗余计数器。
 */
@Data
public class TrustMetricsSnapshot {
    private long daysVisited;
    private long daysVisitedRecent;
    private long postsEnteredRecent;
    private long postsReadRecent;
    private long readTimeSec;
    private long likesReceived;
    private long likesGiven;
    private long postsCreated;
}
