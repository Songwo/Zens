package com.campus.trend.campus_pulse.config.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Base64;

@Data
@Component
@ConfigurationProperties(prefix = "campus.supporter-voucher")
public class SupporterVoucherProperties {
    /** 32 字节随机密钥的 Base64，仅通过服务器环境变量注入。 */
    private String encryptionKey;
    private String redemptionUrl = "https://pip.kdns.fr";

    @PostConstruct
    void validate() {
        if (!StringUtils.hasText(redemptionUrl)) {
            throw new IllegalStateException("campus.supporter-voucher.redemption-url 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(redemptionUrl.trim());
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("campus.supporter-voucher.redemption-url 格式无效", error);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException("campus.supporter-voucher.redemption-url 必须是有效 HTTPS 地址");
        }
        redemptionUrl = uri.toString();
        if (StringUtils.hasText(encryptionKey)) {
            try {
                if (Base64.getDecoder().decode(encryptionKey.trim()).length != 32) {
                    throw new IllegalStateException("SUPPORTER_VOUCHER_ENCRYPTION_KEY 解码后必须为 32 字节");
                }
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException("SUPPORTER_VOUCHER_ENCRYPTION_KEY 必须是有效 Base64", error);
            }
        }
    }
}
