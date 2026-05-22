package com.campus.trend.campus_pulse.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cloudflare Turnstile 配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cloudflare.turnstile")
public class TurnstileProperties {

    /**
     * 是否启用 Turnstile 校验。
     */
    private boolean enabled = true;

    /**
     * 是否允许本地预览环境跳过 Turnstile 校验。
     */
    private boolean allowLocalPreviewSkip = false;

    /**
     * 前端使用的 Site Key，主要用于统一配置管理。
     */
    private String siteKey;

    /**
     * 后端校验 token 时使用的 Secret Key。
     */
    private String secretKey;

    /**
     * 官方校验地址。
     */
    private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    /**
     * 校验接口连接超时秒数。
     */
    private int connectTimeoutSeconds = 5;

    /**
     * 校验接口读取超时秒数。
     */
    private int readTimeoutSeconds = 8;

    /**
     * 出站代理主机，可选。
     */
    private String proxyHost;

    /**
     * 出站代理端口，可选。
     */
    private Integer proxyPort;
}
