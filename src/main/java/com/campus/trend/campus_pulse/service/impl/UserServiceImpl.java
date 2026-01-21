package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.request.UserPasswordUpdateReq;
import com.campus.trend.campus_pulse.dto.request.UserDetailUpdateReq;
import com.campus.trend.campus_pulse.dto.response.UserDetailResp;
import com.campus.trend.campus_pulse.dto.response.UserSimpleResp;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.FileUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;

    @Value("${Web.AvatarUrl}")
    private String url;

    public UserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetailResp getProfile() {
        // 1.从Security上下文中获取用户信息
        AuthUser auUser = SecurityUtils.getAuthenticatedUser();

        // 2.获取用户详细信息
        User sysUser = searchByUsername(auUser.getUsername());
        // 3.构造用户信息响应
        UserDetailResp proFileResponse = new UserDetailResp();
        proFileResponse.setId(sysUser.getId());
        proFileResponse.setUsername(sysUser.getUsername());
        proFileResponse.setEmail(sysUser.getEmail());
        proFileResponse.setAvatar(sysUser.getAvatar());
        proFileResponse.setNickname(sysUser.getNickname());
        proFileResponse.setMajor(sysUser.getMajor());
        proFileResponse.setGrade(sysUser.getGrade());
        proFileResponse.setGender(sysUser.getGender());
        proFileResponse.setSchool(sysUser.getSchool());
        proFileResponse.setRole(sysUser.getRole());
        proFileResponse.setStatus(sysUser.getStatus());
        proFileResponse.setCreateTime(sysUser.getCreateTime());
        proFileResponse.setUpdateTime(sysUser.getUpdateTime());
        proFileResponse.setInterestTags(sysUser.getInterestTags());

        // 尝试自动升级年级
        checkAndUpgradeGrade(sysUser);

        return proFileResponse;
    }

    @Override
    public UserSimpleResp getSimpleProfile() {
        // 1.从Security上下文中获取用户信息
        AuthUser auUser = SecurityUtils.getAuthenticatedUser();

        // 2.获取用户详细信息
        User sysUser = searchByUsername(auUser.getUsername());
        // 3.构造用户信息响应
        UserSimpleResp simpleProfileResponse = new UserSimpleResp();
        simpleProfileResponse.setId(sysUser.getId());
        simpleProfileResponse.setAvatar(sysUser.getAvatar());
        simpleProfileResponse.setNickname(sysUser.getNickname());

        checkAndUpgradeGrade(sysUser);

        return simpleProfileResponse;
    }

    @Override
    public String uploadAvatar(MultipartFile file) {

        String suffix = FileUtils.validateImageAndGetSuffix(file);

        String uploadDir = FileUtils.getProjectRootPath() + "/data/avatar/";

        File folder = new File(uploadDir);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        String fileName = UUID.randomUUID().toString() + suffix;
        File dest = new File(folder, fileName);

        try {
            file.transferTo(dest);
        } catch (IOException e) {
            throw new RuntimeException("头像上传失败", e);
        }

        String avatarUrl = url + fileName;

        // 获取当前登录用户并更新数据库
        try {
            AuthUser authUser = SecurityUtils.getAuthenticatedUser();
            if (authUser != null) {
                User user = new User();
                user.setId(authUser.getUser().getId());
                user.setAvatar(avatarUrl);
                user.setUpdateTime(LocalDateTime.now());
                this.updateById(user);
            }
        } catch (Exception e) {
            log.warn("上传头像时更新用户信息失败: {}", e.getMessage());
            // 不抛出异常，保证上传本身是成功的，但记录日志
        }

        return avatarUrl;
    }

    @Override
    public void updateUserPassword(UserPasswordUpdateReq req) {

        // 1.获取当前用户
        AuthUser auUser = SecurityUtils.getAuthenticatedUser();
        User sysUser = searchByUsername(auUser.getUsername());

        // 2.校验旧密码
        if (!passwordEncoder.matches(req.getOldPassword(), sysUser.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }

        // 3.新旧密码不能一样
        if (passwordEncoder.matches(req.getNewPassword(), sysUser.getPassword())) {
            throw new RuntimeException("新密码不能与旧密码相同");
        }

        // 4.设置新密码（加密）
        sysUser.setPassword(passwordEncoder.encode(req.getNewPassword()));
        sysUser.setUpdateTime(LocalDateTime.now());
        updateById(sysUser);
    }

    @Override
    public void updateUserDetails(UserDetailUpdateReq updateUserDetailRequest) {
        // 1.获取当前用户
        AuthUser auUser = SecurityUtils.getAuthenticatedUser();
        User sysUser = searchByUsername(auUser.getUsername());

        // 2.修改信息
        sysUser.setNickname(updateUserDetailRequest.getNickname());
        sysUser.setMajor(updateUserDetailRequest.getMajor());
        sysUser.setGrade(updateUserDetailRequest.getGrade());
        sysUser.setAvatar(updateUserDetailRequest.getAvatar());
        sysUser.setGender(updateUserDetailRequest.getGender());
        sysUser.setSchool(updateUserDetailRequest.getSchool());
        sysUser.setInterestTags(updateUserDetailRequest.getInterestTags());

        sysUser.setUpdateTime(LocalDateTime.now());

        // 3.执行修改
        updateById(sysUser);
    }

    @Override
    public User searchByUsername(String username) {
        return lambdaQuery().eq(User::getUsername, username).one();
    }

    @Override
    public List<User> searchByGrade(int grade) {
        return lambdaQuery().ge(User::getGrade, grade).list();
    }

    @Override
    public List<User> getUsers() {
        return this.list();
    }

    /** -----------------------内置方法------------------------- */
    /**
     * 检查并自动升级年级
     * 若当前时间距离上次升级（或创建时间）超过1年，则自动+1
     */
    @Override
    public void checkAndUpgradeGrade(User user) {

        LocalDateTime last = user.getLastGradeUpgrade();
        if (last == null) {
            last = user.getCreateTime(); // 没有升级记录则从注册时间算
        }
        
        // 避免 null pointer
        if (last == null) {
             return;
        }

        // 判断是否满一年
        if (last.plusYears(1).isBefore(LocalDateTime.now())) {
            user.setGrade(user.getGrade() + 1);
            user.setLastGradeUpgrade(LocalDateTime.now());

            updateById(user);
        }
    }

}
