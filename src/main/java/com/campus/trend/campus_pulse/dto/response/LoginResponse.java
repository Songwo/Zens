package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

@Data
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private Boolean twoFactorRequired;
    private String twoFactorTicket;

}
