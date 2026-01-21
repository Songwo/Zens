package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.entity.Grade;
import com.campus.trend.campus_pulse.entity.StudentProfile;
import com.campus.trend.campus_pulse.mapper.GradeMapper;
import com.campus.trend.campus_pulse.mapper.StudentProfileMapper;
import com.campus.trend.campus_pulse.service.StudentService;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StudentServiceImpl extends com.baomidou.mybatisplus.extension.service.impl.ServiceImpl<StudentProfileMapper, StudentProfile> implements StudentService {

    @Autowired
    private GradeMapper gradeMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    public StudentProfile getStudentProfile(String userId) {
        // 1. Cache Aside: 先查 Redis
        String key = "student:profile:" + userId;
        String cache = redisTemplate.opsForValue().get(key);
        if (cache != null && !cache.isEmpty()) {
            try {
                return objectMapper.readValue(cache, StudentProfile.class);
            } catch (Exception e) {
                // 序列化失败则忽略缓存，查DB
            }
        }

        // 2. 查 DB
        StudentProfile profile = this.getById(userId);

        // 3. 回写 Redis (TTL 30分钟)
        if (profile != null) {
            try {
                String json = objectMapper.writeValueAsString(profile);
                redisTemplate.opsForValue().set(key, json, java.time.Duration.ofMinutes(30));
            } catch (Exception e) {
                // 忽略写缓存失败
            }
        }
        return profile;
    }

    @Override
    @Transactional
    public void updateProfile(StudentProfile profile) {
        this.saveOrUpdate(profile);
        // 4. Cache Aside: 更新时删除缓存
        String key = "student:profile:" + profile.getUserId();
        redisTemplate.delete(key);
    }


    @Override
    public String checkGraduationStatus(String userId) {
        StudentProfile profile = this.getById(userId);
        if (profile == null) return "学生信息不存在";

        // 计算已通过学分
        List<Grade> passedGrades = gradeMapper.selectList(new LambdaQueryWrapper<Grade>()
                .eq(Grade::getUserId, userId)
                .eq(Grade::getIsPassed, 1));
        
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
