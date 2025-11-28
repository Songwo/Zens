package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.UpdatePasswordRequest;
import com.campus.trend.campus_pulse.dto.request.UpdateUserDetailRequest;
import com.campus.trend.campus_pulse.dto.response.ProFileResponse;
import com.campus.trend.campus_pulse.dto.response.SimpleProfileResponse;
import com.campus.trend.campus_pulse.entity.SysUser;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {

    ProFileResponse GetProFile();

    SimpleProfileResponse GetSimpleProfile();

    List<SysUser> GetUsers();

    String UploadAvatar(MultipartFile file);

    void UpdateUserPassword(UpdatePasswordRequest updatePasswordRequest);

    void UpdateUserDetails(UpdateUserDetailRequest updateUserDetailRequest);

    void autoUpgradeGrade(SysUser user);

}
