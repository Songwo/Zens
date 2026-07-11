package com.campus.trend.campus_pulse.payment;

import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.config.properties.PaymentProperties;
import com.campus.trend.campus_pulse.entity.PaymentOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** 支付宝电脑网站支付（alipay.trade.page.pay）RSA2 适配器。 */
@Component
public class AlipayPagePaymentProvider implements PaymentProvider {
    private static final DateTimeFormatter ALIPAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    public AlipayPagePaymentProvider(PaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String code() {
        return "alipay";
    }

    @Override
    public PaymentCheckout createCheckout(PaymentOrder order) {
        requireEnabled();
        try {
            PaymentProperties.Alipay config = properties.getAlipay();
            Map<String, Object> biz = new LinkedHashMap<>();
            biz.put("out_trade_no", order.getOrderNo());
            biz.put("product_code", "FAST_INSTANT_TRADE_PAY");
            biz.put("total_amount", money(order.getAmountCents()));
            biz.put("subject", safeSubject(order.getPlanNameSnapshot()));
            biz.put("timeout_express", properties.getOrderExpireMinutes() + "m");

            Map<String, String> params = new TreeMap<>();
            params.put("app_id", config.getAppId());
            params.put("method", "alipay.trade.page.pay");
            params.put("format", "JSON");
            params.put("return_url", appendOrderNo(config.getReturnUrl(), order.getOrderNo()));
            params.put("charset", "utf-8");
            params.put("sign_type", "RSA2");
            params.put("timestamp", LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(ALIPAY_TIME));
            params.put("version", "1.0");
            params.put("notify_url", config.getNotifyUrl());
            params.put("biz_content", objectMapper.writeValueAsString(biz));
            params.put("sign", sign(signingContent(params, false), privateKey(config.getMerchantPrivateKey())));
            return new PaymentCheckout(null, config.getGateway() + "?" + encodeQuery(params));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.FAILED, "支付宝收银台创建失败");
        }
    }

    @Override
    public PaymentNotification verifyCallback(String rawBody, Map<String, String> headers) {
        requireEnabled();
        try {
            Map<String, String> params = parseForm(rawBody);
            String signature = required(params, "sign");
            if (!"RSA2".equalsIgnoreCase(params.getOrDefault("sign_type", "RSA2"))) {
                throw invalidCallback("支付宝回调签名类型无效");
            }
            if (!verify(signingContent(params, true), signature,
                    publicKey(properties.getAlipay().getAlipayPublicKey()))) {
                throw invalidCallback("支付宝回调验签失败");
            }
            if (!properties.getAlipay().getAppId().equals(required(params, "app_id"))) {
                throw invalidCallback("支付宝回调 app_id 不匹配");
            }
            if (StringUtils.hasText(properties.getAlipay().getSellerId())
                    && !properties.getAlipay().getSellerId().equals(required(params, "seller_id"))) {
                throw invalidCallback("支付宝回调 seller_id 不匹配");
            }
            String tradeStatus = required(params, "trade_status");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "支付宝交易尚未支付成功");
            }
            String tradeNo = required(params, "trade_no");
            String orderNo = required(params, "out_trade_no");
            int amountCents = new BigDecimal(required(params, "total_amount"))
                    .movePointRight(2).intValueExact();
            LocalDateTime paidAt = LocalDateTime.parse(required(params, "gmt_payment"), ALIPAY_TIME);
            return new PaymentNotification(
                    tradeNo + ":" + tradeStatus,
                    orderNo,
                    tradeNo,
                    "PAID",
                    amountCents,
                    "CNY",
                    paidAt);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw invalidCallback("支付宝回调内容无效");
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || !"alipay".equals(properties.getProvider())) {
            throw new BusinessException(ResultCode.FAILED, "支付宝支付渠道未启用");
        }
    }

    private String money(int cents) {
        return BigDecimal.valueOf(cents, 2).setScale(2).toPlainString();
    }

    private String safeSubject(String subject) {
        String normalized = StringUtils.hasText(subject) ? subject.trim() : "Zens 支持者计划";
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private String appendOrderNo(String returnUrl, String orderNo) {
        return returnUrl + (returnUrl.contains("?") ? "&" : "?") + "orderNo=" + urlEncode(orderNo);
    }

    /** 请求签名仅排除 sign；通知验签按支付宝规则排除 sign 与 sign_type。 */
    private String signingContent(Map<String, String> params, boolean callback) {
        List<String> pairs = new ArrayList<>();
        new TreeMap<>(params).forEach((key, value) -> {
            if ("sign".equals(key) || (callback && "sign_type".equals(key)) || !StringUtils.hasText(value)) return;
            pairs.add(key + "=" + value);
        });
        return String.join("&", pairs);
    }

    private String sign(String content, PrivateKey privateKey) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private boolean verify(String content, String signature, PublicKey publicKey) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(content.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getDecoder().decode(signature));
    }

    private PrivateKey privateKey(String pem) throws Exception {
        byte[] der = Base64.getDecoder().decode(stripPem(pem));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private PublicKey publicKey(String pem) throws Exception {
        byte[] der = Base64.getDecoder().decode(stripPem(pem));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private String stripPem(String value) {
        return value.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
    }

    private String encodeQuery(Map<String, String> params) {
        List<String> pairs = new ArrayList<>();
        params.forEach((key, value) -> pairs.add(urlEncode(key) + "=" + urlEncode(value)));
        return String.join("&", pairs);
    }

    private Map<String, String> parseForm(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            result.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return result;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String required(Map<String, String> params, String key) {
        String value = params.get(key);
        if (!StringUtils.hasText(value)) throw invalidCallback("支付宝回调缺少字段: " + key);
        return value;
    }

    private BusinessException invalidCallback(String message) {
        return new BusinessException(ResultCode.TOKEN_INVALID, message);
    }
}
