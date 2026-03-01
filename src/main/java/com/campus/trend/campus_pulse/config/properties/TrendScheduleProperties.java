package com.campus.trend.campus_pulse.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "trend.schedule")
public class TrendScheduleProperties {

    /**
     * Song：浏览日志保留天数
     */
    private int viewLogRetentionDays = 90;
}
