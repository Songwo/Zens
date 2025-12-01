package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.request.UpdatePasswordRequest;
import com.campus.trend.campus_pulse.dto.request.UpdateUserDetailRequest;
import com.campus.trend.campus_pulse.dto.response.ProFileResponse;
import com.campus.trend.campus_pulse.dto.response.SimpleProfileResponse;
import com.campus.trend.campus_pulse.entity.SysUser;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService extends IService<SysUser> {

    ProFileResponse GetProFile();

    SimpleProfileResponse GetSimpleProfile();

    List<SysUser> GetUsers();

    String UploadAvatar(MultipartFile file);

    void UpdateUserPassword(UpdatePasswordRequest updatePasswordRequest);

    void UpdateUserDetails(UpdateUserDetailRequest updateUserDetailRequest);

    SysUser searchByUsername(String username);

    List<SysUser> searchByGrade(int grade);

    List<SysUser> searchAll();

    // Warning ：内置方法禁止本服务引入自己的方法 ！！！
    void autoUpgradeGrade(SysUser user);

}
