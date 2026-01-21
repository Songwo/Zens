package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.entity.Course;
import com.campus.trend.campus_pulse.entity.CourseSelection;
import com.campus.trend.campus_pulse.entity.Grade;
import com.campus.trend.campus_pulse.mapper.CourseMapper;
import com.campus.trend.campus_pulse.mapper.CourseSelectionMapper;
import com.campus.trend.campus_pulse.mapper.GradeMapper;
import com.campus.trend.campus_pulse.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseServiceImpl implements CourseService {

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseSelectionMapper selectionMapper;

    @Autowired
    private GradeMapper gradeMapper;

    @Override
    public IPage<Course> getCourseList(int page, int pageSize, String keyword) {
        Page<Course> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Course> query = new LambdaQueryWrapper<Course>()
                .like(keyword != null, Course::getName, keyword)
                .eq(Course::getStatus, 1);
        return courseMapper.selectPage(p, query);
    }

    @Override
    @Transactional
    public void selectCourse(String userId, Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) throw new RuntimeException("课程不存在");
        if (course.getCurrentCapacity() >= course.getMaxCapacity()) throw new RuntimeException("选课人数已满");

        // 查重
        Long count = selectionMapper.selectCount(new LambdaQueryWrapper<CourseSelection>()
                .eq(CourseSelection::getUserId, userId)
                .eq(CourseSelection::getCourseId, courseId));
        if (count > 0) throw new RuntimeException("已选择该课程，请勿重复操作");

        CourseSelection selection = new CourseSelection();
        selection.setUserId(userId);
        selection.setCourseId(courseId);
        selectionMapper.insert(selection);

        // 更新人数
        course.setCurrentCapacity(course.getCurrentCapacity() + 1);
        courseMapper.updateById(course);
    }

    @Override
    @Transactional
    public void dropCourse(String userId, Long courseId) {
        int deleted = selectionMapper.delete(new LambdaQueryWrapper<CourseSelection>()
                .eq(CourseSelection::getUserId, userId)
                .eq(CourseSelection::getCourseId, courseId));
        
        if (deleted > 0) {
            Course course = courseMapper.selectById(courseId);
            course.setCurrentCapacity(Math.max(0, course.getCurrentCapacity() - 1));
            courseMapper.updateById(course);
        }
    }

    @Override
    public List<Course> getMySelectedCourses(String userId) {
        List<CourseSelection> selections = selectionMapper.selectList(new LambdaQueryWrapper<CourseSelection>()
                .eq(CourseSelection::getUserId, userId));
        if (selections.isEmpty()) return List.of();
        
        List<Long> ids = selections.stream().map(CourseSelection::getCourseId).collect(Collectors.toList());
        return courseMapper.selectBatchIds(ids);
    }

    @Override
    public List<Grade> getMyGrades(String userId, String semester) {
        return gradeMapper.selectList(new LambdaQueryWrapper<Grade>()
                .eq(Grade::getUserId, userId)
                .eq(semester != null, Grade::getSemester, semester));
    }

    @Override
    public void saveGrade(Grade grade) {
        grade.setIsPassed(grade.getScore().doubleValue() >= 60 ? 1 : 0);
        gradeMapper.insert(grade);
    }
}
