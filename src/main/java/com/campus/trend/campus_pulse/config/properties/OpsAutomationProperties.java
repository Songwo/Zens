package com.campus.trend.campus_pulse.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "campus.ops")
public class OpsAutomationProperties {
  private String serviceId = "zens-ops";
  private String authorUsername = "zens_ops";
  private int dailyPublishLimit = 1;
  private int dailyReplyLimit = 10;
  private int firstApprovalCount = 30;
  private boolean autoPublish = false;
  private boolean circuitOpen = true;
}
