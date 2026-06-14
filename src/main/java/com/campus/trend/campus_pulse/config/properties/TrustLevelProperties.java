package com.campus.trend.campus_pulse.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 信任等级 TL0-TL4 阈值配置（借鉴 Discourse 社区自治模型，校园场景适配后的默认值，均可通过环境变量覆盖）
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.trust-level")
public class TrustLevelProperties {

    private Tl1 tl1 = new Tl1();
    private Tl2 tl2 = new Tl2();
    private Tl3 tl3 = new Tl3();
    private Flag flag = new Flag();
    /**
     * 全量重算 cron，默认每日 02:30
     */
    private String recalcCron = "0 30 2 * * ?";

    @Data
    public static class Tl1 {
        /** 进入不同帖子数 */
        private int requiresPostsEntered = 3;
        /** 读帖数 */
        private int requiresPostsRead = 15;
        /** 累计阅读分钟数 */
        private int requiresReadMinutes = 5;
        /** 注册满多少天 */
        private int requiresDays = 1;
    }

    @Data
    public static class Tl2 {
        private int requiresDaysVisited = 7;
        private int requiresPostsEntered = 15;
        private int requiresPostsRead = 60;
        private int requiresReadMinutes = 30;
        private int requiresLikesReceived = 1;
    }

    @Data
    public static class Tl3 {
        /** 滚动窗口天数 */
        private int windowDays = 100;
        /** 窗口内访问天数下限 */
        private int requiresDaysVisited = 50;
        private int requiresPostsRead = 500;
        private int requiresLikesReceived = 10;
        private int requiresLikesGiven = 10;
        private int requiresPostsCreated = 10;
    }

    @Data
    public static class Flag {
        /** 自治隐藏阈值：flag 权重总和达到此值自动隐藏 */
        private int autoHideThreshold = 10;
    }
}
