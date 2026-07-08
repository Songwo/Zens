package com.campus.trend.campus_pulse.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "campus.agent")
public class AgentServiceProperties {

    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:7810";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 20000;
}
