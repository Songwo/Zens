package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.entity.SysCourse;
import com.campus.trend.campus_pulse.entity.SysCourseSelection;
import com.campus.trend.campus_pulse.entity.SysGrade;
import com.campus.trend.campus_pulse.mapper.SysCourseMapper;
import com.campus.trend.campus_pulse.mapper.SysCourseSelectionMapper;
import com.campus.trend.campus_pulse.mapper.SysGradeMapper;
import com.campus.trend.campus_pulse.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseServiceImpl implements CourseService {

    @Autowired
    private SysCourseMapper courseMapper;

    @Autowired
    private SysCourseSelectionMapper selectionMapper;

    @Autowired
    private SysGradeMapper gradeMapper;

    @Override
    public IPage<SysCourse> getCourseList(int page, int pageSize, String keyword) {
        Page<SysCourse> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<SysCourse> query = new LambdaQueryWrapper<SysCourse>()
                .like(keyword != null, SysCourse::getName, keyword)
                .eq(SysCourse::getStatus, 1);
        return courseMapper.selectPage(p, query);
    }

    @Override
    @Transactional
    public void selectCourse(String userId, Long courseId) {
        SysCourse course = courseMapper.selectById(courseId);
        if (course == null) throw new RuntimeException("课程不存在");
        if (course.getCurrentCapacity() >= course.getMaxCapacity()) throw new RuntimeException("选课人数已满");

        // 查重
        Long count = selectionMapper.selectCount(new LambdaQueryWrapper<SysCourseSelection>()
                .eq(SysCourseSelection::getUserId, userId)
                .eq(SysCourseSelection::getCourseId, courseId));
        if (count > 0) throw new RuntimeException("已选择该课程，请勿重复操作");

        SysCourseSelection selection = new SysCourseSelection();
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
        int deleted = selectionMapper.delete(new LambdaQueryWrapper<SysCourseSelection>()
                .eq(SysCourseSelection::getUserId, userId)
                .eq(SysCourseSelection::getCourseId, courseId));
        
        if (deleted > 0) {
            SysCourse course = courseMapper.selectById(courseId);
            course.setCurrentCapacity(Math.max(0, course.getCurrentCapacity() - 1));
            courseMapper.updateById(course);
        }
    }

    @Override
    public List<SysCourse> getMySelectedCourses(String userId) {
        List<SysCourseSelection> selections = selectionMapper.selectList(new LambdaQueryWrapper<SysCourseSelection>()
                .eq(SysCourseSelection::getUserId, userId));
        if (selections.isEmpty()) return List.of();
        
        List<Long> ids = selections.stream().map(SysCourseSelection::getCourseId).collect(Collectors.toList());
        return courseMapper.selectBatchIds(ids);
    }

    @Override
    public List<SysGrade> getMyGrades(String userId, String semester) {
        return gradeMapper.selectList(new LambdaQueryWrapper<SysGrade>()
                .eq(SysGrade::getUserId, userId)
                .eq(semester != null, SysGrade::getSemester, semester));
    }

    @Override
    public void saveGrade(SysGrade grade) {
        grade.setIsPassed(grade.getScore().doubleValue() >= 60 ? 1 : 0);
        gradeMapper.insert(grade);
    }
}
