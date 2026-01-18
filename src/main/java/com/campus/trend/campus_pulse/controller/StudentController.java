package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.entity.SysStudentProfile;
import com.campus.trend.campus_pulse.service.StudentService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "学生信息管理")
@RestController
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private StudentService studentService;

    @Operation(summary = "获取我的档案")
    @GetMapping("/profile")
    public Result<SysStudentProfile> getMyProfile() {
        String userId = SecurityUtils.getCurrentUserId();
        return Result.success(studentService.getStudentProfile(userId));
    }

    @Operation(summary = "更新档案 (管理员或本人)")
    @PostMapping("/profile/update")
    public Result<Void> updateProfile(@RequestBody SysStudentProfile profile) {
        // 权限校验逻辑可在 Filter 或此处实现
        studentService.updateProfile(profile);
        return Result.success();
    }

    @Operation(summary = "毕业资格自检")
    @GetMapping("/graduation/check")
    public Result<String> checkGraduation() {
        String userId = SecurityUtils.getCurrentUserId();
        return Result.success(studentService.checkGraduationStatus(userId));
    }
}
