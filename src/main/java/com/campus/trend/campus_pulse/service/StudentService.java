package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.entity.SysStudentProfile;

public interface StudentService {
    /**
     * 获取学生详细档案
     */
    SysStudentProfile getStudentProfile(String userId);

    /**
     * 更新学生档案 (管理员或本人部分信息)
     */
    void updateProfile(SysStudentProfile profile);

    /**
     * 毕业资格自检
     * @return 状态说明
     */
    String checkGraduationStatus(String userId);
}
