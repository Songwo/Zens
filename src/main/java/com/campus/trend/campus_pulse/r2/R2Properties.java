package com.campus.trend.campus_pulse.r2;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "campus.r2")
public class R2Properties {

    private boolean enabled = true;
    private String endpoint;
    private String region = "auto";
    private String accessKeyId;
    private String secretAccessKey;
    private String bucket;
    private String publicBaseUrl;
    private long presignTtlSeconds = 3600;
    private int singlePutThresholdMb = 5;
    private int partSizeMb = 8;
    private int maxImageSizeMb = 15;
    private int maxVideoSizeMb = 1024;
    private String allowedImageExtensions = ".jpg,.jpeg,.png,.gif,.webp";
    private String allowedVideoExtensions = ".mp4,.webm,.ogg,.mov";
    private String cacheControl;
}
