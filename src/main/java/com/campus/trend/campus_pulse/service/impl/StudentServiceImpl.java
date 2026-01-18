package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.entity.SysGrade;
import com.campus.trend.campus_pulse.entity.SysStudentProfile;
import com.campus.trend.campus_pulse.mapper.SysGradeMapper;
import com.campus.trend.campus_pulse.mapper.SysStudentProfileMapper;
import com.campus.trend.campus_pulse.service.StudentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StudentServiceImpl implements StudentService {

    @Autowired
    private SysStudentProfileMapper studentProfileMapper;

    @Autowired
    private SysGradeMapper gradeMapper;

    @Override
    public SysStudentProfile getStudentProfile(String userId) {
        return studentProfileMapper.selectById(userId);
    }

    @Override
    @Transactional
    public void updateProfile(SysStudentProfile profile) {
        studentProfileMapper.insertOrUpdate(profile);
    }

    @Override
    public String checkGraduationStatus(String userId) {
        SysStudentProfile profile = studentProfileMapper.selectById(userId);
        if (profile == null) return "学生信息不存在";

        // 计算已通过学分
        List<SysGrade> passedGrades = gradeMapper.selectList(new LambdaQueryWrapper<SysGrade>()
                .eq(SysGrade::getUserId, userId)
                .eq(SysGrade::getIsPassed, 1));
        
        BigDecimal totalCredits = passedGrades.stream()
                .map(g -> g.getScore()) // 这里简化逻辑，实际应关联 Course 表取学分
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 假设毕业需要 120 学分
        if (totalCredits.compareTo(new BigDecimal("120")) >= 0) {
            return "已达标：修满 " + totalCredits + " 学分";
        } else {
            return "未达标：当前 " + totalCredits + " 学分，尚缺 " + new BigDecimal("120").subtract(totalCredits) + " 学分";
        }
    }
}
