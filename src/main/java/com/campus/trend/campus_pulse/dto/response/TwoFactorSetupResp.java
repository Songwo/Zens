package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

@Data
public class TwoFactorSetupResp {

    private String secret;
    private String otpauthUri;
    private String qrCodeUrl;
    private Integer expireSeconds;
}
