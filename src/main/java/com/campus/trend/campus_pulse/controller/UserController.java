package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.UserPasswordUpdateReq;
import com.campus.trend.campus_pulse.dto.request.UserDetailUpdateReq;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
public class UserController {

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    private final UserService userService;

    private final AuthService authService;

    @GetMapping("/profile")
    public Result<?> getProfile() {
        return Result.success(userService.getProfile());
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

    @PutMapping("/avatar")
    public Result<?> updateAvatar(@RequestPart("avatar") MultipartFile file) {
        String url = userService.uploadAvatar(file);
        return Result.success(url);
    }

    @PostMapping("/update-pwd")
    public Result<?> updatePwd(@Valid @RequestBody UserPasswordUpdateReq updatePasswordRequest) {
        userService.updateUserPassword(updatePasswordRequest);
        authService.logout();
        return Result.success();
    }

    @PostMapping("/update-udetail")
    public Result<?> updateDetail(@Valid @RequestBody UserDetailUpdateReq updateUserDetailRequest) {
        userService.updateUserDetails(updateUserDetailRequest);
        return Result.success();
    }

}
