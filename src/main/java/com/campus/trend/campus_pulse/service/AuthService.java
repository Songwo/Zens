package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.LoginRequest;

public interface AuthService {

    String login(LoginRequest loginRequest);
}
