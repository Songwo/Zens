package com.campus.trend.campus_pulse.payment;

import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentProviderRegistry {
    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.code().toLowerCase(Locale.ROOT), Function.identity()));
    }

    public PaymentProvider require(String code) {
        PaymentProvider provider = providers.get(code == null ? "" : code.toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new BusinessException(ResultCode.FAILED, "支付渠道尚未配置");
        }
        return provider;
    }
}
