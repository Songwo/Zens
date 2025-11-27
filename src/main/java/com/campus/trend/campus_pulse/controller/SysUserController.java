package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sys-user")
public class SysUserController {

    public SysUserController(UserService userService) {
        this.userService = userService;
    }

    private final UserService userService;

    @GetMapping("/profile")
    public Result<?> getProfile() {
        return Result.success(userService.getProFile());
    }

    @GetMapping("/simple-profile")
    public Result<?> getSimpleProfile() {
        return Result.success(userService.getSimpleProfile());
    }

    @GetMapping("/test")
    public String Test() {
        return "test";
    }

    @GetMapping("/all")
    public Result<?> getAll() {
        return Result.success(userService.getUsers());
    }
}
