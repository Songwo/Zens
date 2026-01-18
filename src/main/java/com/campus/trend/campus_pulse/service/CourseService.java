package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.entity.SysCourse;
import com.campus.trend.campus_pulse.entity.SysGrade;

import java.util.List;

public interface CourseService {
    // 课程列表
    IPage<SysCourse> getCourseList(int page, int pageSize, String keyword);

    // 选课
    void selectCourse(String userId, Long courseId);

    // 退课
    void dropCourse(String userId, Long courseId);

    // 获取已选课程
    List<SysCourse> getMySelectedCourses(String userId);

    // 获取成绩单
    List<SysGrade> getMyGrades(String userId, String semester);

    // 录入成绩 (管理员)
    void saveGrade(SysGrade grade);
}
