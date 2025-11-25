package com.campus.trend.campus_pulse.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/sys-user")
public class SysUserController {

    @GetMapping("/test")
    public String Test(){
        return "test";
    }
}
