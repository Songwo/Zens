package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AdminController {

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    private final AuthService authService;

}
