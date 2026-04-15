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
}
