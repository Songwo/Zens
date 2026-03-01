package com.campus.trend.campus_pulse.dto.request;

import lombok.Data;

@Data
public class LoginReq {

    /**
     * Song：登录账号（用户名或邮箱，密码登录时使用）
     */
    private String account;

    /**
     * Song：密码（密码登录模式）
     */
    private String password;

    /**
     * Song：登录邮箱（验证码登录模式）
     */
    private String email;

    /**
     * Song：邮箱验证码（验证码登录模式）
     */
    private String code;

    /**
     * Song：说明
     */
    private String loginType;

    /**
     * Song：记住我
     */
    private boolean rememberMe = false;

    /**
     * Song：谷歌验证器二步验证码（用户开启后可传）
     */
    private String twoFactorCode;
}
