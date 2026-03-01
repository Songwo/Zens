package com.campus.trend.campus_pulse.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "campus.support")
public class SupportContactProperties {

    /**
     * Song：私信管理员账号（可配置）
     */
    private String adminUsername = "admin";

    /**
     * Song：展示名称（可选）
     */
    private String adminDisplayName = "社区管理员";
}
