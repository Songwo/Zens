package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.request.UserPasswordUpdateReq;
import com.campus.trend.campus_pulse.dto.request.UserDetailUpdateReq;
import com.campus.trend.campus_pulse.dto.response.UserDetailResp;
import com.campus.trend.campus_pulse.dto.response.UserSimpleResp;
import com.campus.trend.campus_pulse.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService extends IService<User> {

    UserDetailResp getProfile();

    UserSimpleResp getSimpleProfile();

    List<User> getUsers();

    String uploadAvatar(MultipartFile file);

    void updateUserPassword(UserPasswordUpdateReq updatePasswordRequest);

    void updateUserDetails(UserDetailUpdateReq updateUserDetailRequest);

    User searchByUsername(String username);

    List<User> searchByGrade(int grade);

    // Warning ：内置方法禁止本服务引入自己的方法 ！！！
    void checkAndUpgradeGrade(User user);
}
