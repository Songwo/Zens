package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.entity.StudentProfile;

public interface StudentService {
    /**
     * 获取学生详细档案
     */
    StudentProfile getStudentProfile(String userId);

    /**
     * 更新学生档案 (管理员或本人部分信息)
     */
    void updateProfile(StudentProfile profile);

    /**
     * 毕业资格自检
     * @return 状态说明
     */
    String checkGraduationStatus(String userId);
}
