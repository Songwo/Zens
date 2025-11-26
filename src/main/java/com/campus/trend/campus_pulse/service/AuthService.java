package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.LoginRequest;
import com.campus.trend.campus_pulse.dto.RegisterRequest;


public interface AuthService {

    String login(LoginRequest loginRequest);

    boolean register(RegisterRequest registerRequest);
}
