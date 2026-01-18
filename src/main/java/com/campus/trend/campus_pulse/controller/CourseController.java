package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.entity.SysCourse;
import com.campus.trend.campus_pulse.entity.SysGrade;
import com.campus.trend.campus_pulse.service.CourseService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "教务管理 (选课与成绩)")
@RestController
@RequestMapping("/course")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @Operation(summary = "获取选课列表")
    @GetMapping("/list")
    public Result<IPage<SysCourse>> getCourseList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(courseService.getCourseList(page, pageSize, keyword));
    }

    @Operation(summary = "学生选课")
    @PostMapping("/select/{courseId}")
    public Result<Void> selectCourse(@PathVariable Long courseId) {
        String userId = SecurityUtils.getCurrentUserId();
        courseService.selectCourse(userId, courseId);
        return Result.success();
    }

    @Operation(summary = "学生退课")
    @PostMapping("/drop/{courseId}")
    public Result<Void> dropCourse(@PathVariable Long courseId) {
        String userId = SecurityUtils.getCurrentUserId();
        courseService.dropCourse(userId, courseId);
        return Result.success();
    }

    @Operation(summary = "获取我已选的课程")
    @GetMapping("/my-courses")
    public Result<List<SysCourse>> getMyCourses() {
        String userId = SecurityUtils.getCurrentUserId();
        return Result.success(courseService.getMySelectedCourses(userId));
    }

    @Operation(summary = "获取我的成绩单")
    @GetMapping("/my-grades")
    public Result<List<SysGrade>> getMyGrades(@RequestParam(required = false) String semester) {
        String userId = SecurityUtils.getCurrentUserId();
        return Result.success(courseService.getMyGrades(userId, semester));
    }

    @Operation(summary = "录入成绩 (管理员)")
    @PostMapping("/grade/save")
    public Result<Void> saveGrade(@RequestBody SysGrade grade) {
        courseService.saveGrade(grade);
        return Result.success();
    }
}
