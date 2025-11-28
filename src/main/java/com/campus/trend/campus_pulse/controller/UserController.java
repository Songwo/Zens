package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.dto.request.UpdatePasswordRequest;
import com.campus.trend.campus_pulse.dto.request.UpdateUserDetailRequest;
import com.campus.trend.campus_pulse.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/sys-user")
public class UserController {

    public UserController(UserService userService) {
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

    @PutMapping("/avatar")
    public Result<?> updateAvatar(@RequestPart("avatar") MultipartFile file) {
        String url = userService.UploadAvatar(file);
        return Result.success(url);
    }

    @PostMapping("/update-pwd")
    public Result<?> updatePwd(@Valid @RequestBody UpdatePasswordRequest updatePasswordRequest) {
        userService.UpdateUserPassword(updatePasswordRequest);
        return Result.success();
    }

    @PostMapping("/update-udetail")
    public Result<?> updateDetail(@Valid @RequestBody UpdateUserDetailRequest updateUserDetailRequest) {
        userService.UpdateUserDetails(updateUserDetailRequest);
        return Result.success();
    }

}
