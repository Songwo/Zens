package com.campus.trend.campus_pulse.service;

public interface TurnstileService {

    /**
     * 校验登录时提交的 Turnstile token，失败时抛出业务异常。
     */
    void verifyLoginToken(String responseToken, String remoteIp);
}
