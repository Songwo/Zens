package com.campus.trend.campus_pulse.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "recommendation.weights")
public class RecommendationWeightProperties {

    /**
     * Song：相关度分数组件权重
     */
    private double tagScoreWeight = 1.0;
    private double timeFactorWeight = 1.0;
    private double engagementFactorWeight = 1.0;

    /**
     * Song：时间衰减参数
     */
    private double timeDecayDays = 7.0;
    private double timeDecayExponent = 1.2;

    /**
     * Song：互动分参数
     */
    private double likeWeight = 1.0;
    private double commentWeight = 2.0;
    private double collectWeight = 1.5;
    private double engagementDivisor = 100.0;
    private double maxEngagementBoost = 1.0;

    /**
     * Song：推荐池参数
     */
    private int candidateLimit = 200;
    private int anonymousHotLimit = 50;
    private int interestLimit = 20;
    private int cfLimit = 10;
    private int hotLimit = 20;
}
