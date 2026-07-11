package com.campus.trend.campus_pulse.config.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Data
@Component
@ConfigurationProperties(prefix = "campus.payment")
public class PaymentProperties {
    /** 总开关。默认关闭，未配置真实渠道时不能创建现金订单。 */
    private boolean enabled = false;
    /** disabled / mock / 后续接入的真实渠道代码。 */
    private String provider = "disabled";
    private int orderExpireMinutes = 30;
    private boolean allowMock = false;
    private String mockWebhookSecret;
    @NestedConfigurationProperty
    private Alipay alipay = new Alipay();

    private final Environment environment;

    public PaymentProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        provider = StringUtils.hasText(provider) ? provider.trim().toLowerCase() : "disabled";
        if (orderExpireMinutes < 5 || orderExpireMinutes > 1440) {
            throw new IllegalStateException("campus.payment.order-expire-minutes 必须在 5~1440 之间");
        }
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
        if (production && ("mock".equals(provider) || allowMock)) {
            throw new IllegalStateException("生产环境禁止启用 mock 支付渠道");
        }
        if (enabled && "disabled".equals(provider)) {
            throw new IllegalStateException("支付总开关已开启，但未配置真实支付渠道");
        }
        if ("mock".equals(provider)) {
            if (!allowMock) {
                throw new IllegalStateException("mock 支付仅在 campus.payment.allow-mock=true 时可用");
            }
            if (!StringUtils.hasText(mockWebhookSecret) || mockWebhookSecret.length() < 32) {
                throw new IllegalStateException("mock webhook secret 必须至少 32 字符");
            }
        }
        if (enabled && "alipay".equals(provider)) {
            if (!StringUtils.hasText(alipay.appId)
                    || !StringUtils.hasText(alipay.merchantPrivateKey)
                    || !StringUtils.hasText(alipay.alipayPublicKey)
                    || !StringUtils.hasText(alipay.notifyUrl)
                    || !StringUtils.hasText(alipay.returnUrl)) {
                throw new IllegalStateException("支付宝已启用，但 app-id、商户私钥、支付宝公钥或回调地址不完整");
            }
            if (!alipay.notifyUrl.startsWith("https://") || !alipay.returnUrl.startsWith("https://")) {
                throw new IllegalStateException("支付宝 notify-url 与 return-url 必须使用 HTTPS");
            }
        }
    }

    @Data
    public static class Alipay {
        private String appId;
        private String gateway = "https://openapi.alipay.com/gateway.do";
        /** 应用私钥（PKCS#8），仅保存在服务器密钥配置中。 */
        private String merchantPrivateKey;
        /** 支付宝开放平台公钥，不是应用公钥。 */
        private String alipayPublicKey;
        private String notifyUrl;
        private String returnUrl;
        /** 可选；配置后会同时校验异步通知 seller_id。 */
        private String sellerId;
    }
}
