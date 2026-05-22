package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.config.TurnstileProperties;
import com.campus.trend.campus_pulse.service.TurnstileService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

/**
 * Cloudflare Turnstile 服务端校验实现。
 */
@Slf4j
@Service
public class TurnstileServiceImpl implements TurnstileService {

    private static final Duration TOKEN_MAX_AGE = Duration.ofMinutes(5);

    private final TurnstileProperties properties;
    private final RestTemplate restTemplate;

    public TurnstileServiceImpl(TurnstileProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate(buildRequestFactory(properties));
    }

    @Override
    public void verifyLoginToken(String responseToken, String remoteIp) {
        if (!properties.isEnabled()) {
            log.debug("Turnstile 校验已禁用，跳过登录验证: remoteIp={}", remoteIp);
            return;
        }
        if (!StringUtils.hasText(properties.getSecretKey())) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Turnstile Secret Key 未配置");
        }
        if (!StringUtils.hasText(responseToken)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "请先完成人机验证");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.getSecretKey());
        form.add("response", responseToken);
        if (StringUtils.hasText(remoteIp)) {
            form.add("remoteip", remoteIp);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        TurnstileVerifyResponse verifyResponse;
        try {
            verifyResponse = restTemplate.postForObject(
                    properties.getVerifyUrl(),
                    new HttpEntity<>(form, headers),
                    TurnstileVerifyResponse.class);
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            log.error(
                    "Turnstile 校验响应异常: remoteIp={}, verifyUrl={}, status={}, proxy={}:{}, responseBody={}",
                    remoteIp,
                    properties.getVerifyUrl(),
                    ex.getStatusCode(),
                    properties.getProxyHost(),
                    properties.getProxyPort(),
                    responseBody,
                    ex);
            if (containsSecretConfigError(responseBody)) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "人机验证配置错误，请联系管理员");
            }
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "人机验证服务暂不可用，请稍后重试");
        } catch (RestClientException ex) {
            log.error(
                    "Turnstile 校验请求失败: remoteIp={}, verifyUrl={}, proxy={}:{}, rootCause={}",
                    remoteIp,
                    properties.getVerifyUrl(),
                    properties.getProxyHost(),
                    properties.getProxyPort(),
                    rootCauseMessage(ex),
                    ex);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "人机验证服务暂不可用，请稍后重试");
        }

        if (verifyResponse == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "人机验证服务响应为空，请稍后重试");
        }
        if (!verifyResponse.isSuccess()) {
            log.warn("Turnstile 校验失败: remoteIp={}, errors={}", remoteIp, verifyResponse.getErrorCodes());
            throw new BusinessException(ResultCode.VALIDATE_FAILED, resolveFailureMessage(verifyResponse.getErrorCodes()));
        }
        if (isChallengeExpired(verifyResponse.getChallengeTs())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "人机验证已过期，请重试");
        }
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(TurnstileProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(Math.max(properties.getConnectTimeoutSeconds(), 1)).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(Math.max(properties.getReadTimeoutSeconds(), 1)).toMillis());
        if (StringUtils.hasText(properties.getProxyHost()) && properties.getProxyPort() != null) {
            factory.setProxy(new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress(properties.getProxyHost().trim(), properties.getProxyPort())));
        }
        return factory;
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? null : current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    private boolean isChallengeExpired(String challengeTs) {
        if (!StringUtils.hasText(challengeTs)) {
            return false;
        }
        try {
            OffsetDateTime solvedAt = OffsetDateTime.parse(challengeTs);
            return solvedAt.plus(TOKEN_MAX_AGE).isBefore(OffsetDateTime.now());
        } catch (Exception ex) {
            log.debug("Turnstile challenge_ts 解析失败: {}", challengeTs, ex);
            return false;
        }
    }

    private boolean containsSecretConfigError(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return false;
        }
        return responseBody.contains("invalid-input-secret")
                || responseBody.contains("missing-input-secret");
    }

    private String resolveFailureMessage(List<String> errorCodes) {
        if (errorCodes == null || errorCodes.isEmpty()) {
            return "人机验证失败，请重试";
        }
        if (errorCodes.contains("invalid-input-secret") || errorCodes.contains("missing-input-secret")) {
            return "人机验证配置错误，请联系管理员";
        }
        if (errorCodes.contains("timeout-or-duplicate")) {
            return "人机验证已过期，请重试";
        }
        if (errorCodes.contains("missing-input-response") || errorCodes.contains("invalid-input-response")) {
            return "人机验证无效，请重新完成验证";
        }
        return "人机验证失败，请重试";
    }

    @Data
    private static class TurnstileVerifyResponse {
        private boolean success;
        @JsonProperty("challenge_ts")
        private String challengeTs;
        private String hostname;
        @JsonProperty("error-codes")
        private List<String> errorCodes;
        private String action;
        private String cdata;
    }
}
