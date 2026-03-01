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
    private final com.campus.trend.campus_pulse.service.ContentSecurityService contentSecurityService;

    @Value("${Web.AvatarUrl}")
    private String url;

    public UserServiceImpl(PasswordEncoder passwordEncoder,
            com.campus.trend.campus_pulse.service.ContentSecurityService contentSecurityService) {
        this.passwordEncoder = passwordEncoder;
        this.contentSecurityService = contentSecurityService;
    }

    @Override
    public UserDetailResp getProfile() {
        // Song：说明
        AuthUser auUser = SecurityUtils.getAuthenticatedUser();

        // Song：2.获取用户详细信息
        User sysUser = searchByUsername(auUser.getUsername());

        String role = sysUser.getRole() != null ? sysUser.getRole() : "ROLE_USER";
        List<String> roleCodes = List.of(role);

        // Song：构造用户信息响应
        UserDetailResp proFileResponse = new UserDetailResp();
        proFileResponse.setId(sysUser.getId());
        proFileResponse.setUsername(sysUser.getUsername());
        proFileResponse.setEmail(sysUser.getEmail());
        proFileResponse.setAvatar(sysUser.getAvatar());
        proFileResponse.setNickname(sysUser.getNickname());
        proFileResponse.setBio(sysUser.getBio());
        proFileResponse.setMajor(sysUser.getMajor());
        proFileResponse.setEnrollmentYear(sysUser.getEnrollmentYear() != null ? sysUser.getEnrollmentYear() : 0);
        proFileResponse.setGender(sysUser.getGender());
        proFileResponse.setSchool(sysUser.getSchool());
        proFileResponse.setStatus(sysUser.getStatus());
        proFileResponse.setCreateTime(sysUser.getCreateTime());
        proFileResponse.setUpdateTime(sysUser.getUpdateTime());
        proFileResponse.setInterestTags(sysUser.getInterestTags());
        proFileResponse.setTwoFactorEnabled(sysUser.getTwoFactorEnabled() != null ? sysUser.getTwoFactorEnabled() : 0);
        proFileResponse.setEmailNotifyEnabled(sysUser.getEmailNotifyEnabled() != null ? sysUser.getEmailNotifyEnabled() : 1);
        proFileResponse.setGithubBound(sysUser.getGithubId() != null && !sysUser.getGithubId().isBlank());
        proFileResponse.setRoles(roleCodes);

        return proFileResponse;
    }

    @Override
    public UserSimpleResp getSimpleProfile() {
        // Song：说明
        AuthUser auUser = SecurityUtils.getAuthenticatedUser();

        // Song：2.获取用户详细信息
        User sysUser = searchByUsername(auUser.getUsername());
        // Song：3.构造用户信息响应
        UserSimpleResp simpleProfileResponse = new UserSimpleResp();
        simpleProfileResponse.setId(sysUser.getId());
        simpleProfileResponse.setAvatar(sysUser.getAvatar());
        simpleProfileResponse.setNickname(sysUser.getNickname());

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

        // Song：获取当前登录用户并更新数据库
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
            // Song：不抛出异常，保证上传本身是成功的，但记录日志
        }

        return avatarUrl;
    }

    @Override
    public void updateUserPassword(UserPasswordUpdateReq req) {

        // Song：1.获取当前用户
        AuthUser auUser = SecurityUtils.getAuthenticatedUser();
        User sysUser = searchByUsername(auUser.getUsername());

        // Song：2.校验旧密码
        if (!passwordEncoder.matches(req.getOldPassword(), sysUser.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }

        // Song：3.新旧密码不能一样
        if (passwordEncoder.matches(req.getNewPassword(), sysUser.getPassword())) {
            throw new RuntimeException("新密码不能与旧密码相同");
        }

        // Song：4.设置新密码（加密）
        sysUser.setPassword(passwordEncoder.encode(req.getNewPassword()));
        sysUser.setUpdateTime(LocalDateTime.now());
        updateById(sysUser);
    }

    @Override
    public void updateUserDetails(UserDetailUpdateReq updateUserDetailRequest) {
        // Song：1.获取当前用户
        AuthUser auUser = SecurityUtils.getAuthenticatedUser();
        User sysUser = searchByUsername(auUser.getUsername());

        // Song：安全检查与敏感词过滤
        String nickname = updateUserDetailRequest.getNickname();
        if (nickname != null && contentSecurityService.containsSensitiveWords(nickname)) {
            nickname = contentSecurityService.filterSensitiveWords(nickname);
        }

        String bio = updateUserDetailRequest.getBio();
        if (bio != null && contentSecurityService.containsSensitiveWords(bio)) {
            bio = contentSecurityService.filterSensitiveWords(bio);
        }

        // Song：2.修改信息
        sysUser.setNickname(nickname);
        sysUser.setBio(bio);
        sysUser.setMajor(updateUserDetailRequest.getMajor());
        sysUser.setEnrollmentYear(updateUserDetailRequest.getEnrollmentYear());
        sysUser.setAvatar(updateUserDetailRequest.getAvatar());
        sysUser.setGender(updateUserDetailRequest.getGender());
        sysUser.setSchool(updateUserDetailRequest.getSchool());
        sysUser.setInterestTags(updateUserDetailRequest.getInterestTags());

        sysUser.setUpdateTime(LocalDateTime.now());

        // Song：3.执行修改
        updateById(sysUser);
    }

    @Override
    public User searchByUsername(String username) {
        return lambdaQuery().eq(User::getUsername, username).one();
    }

    @Override
    public List<User> searchByGrade(int enrollmentYear) {
        return lambdaQuery().ge(User::getEnrollmentYear, enrollmentYear).list();
    }

    @Override
    public List<User> getUsers() {
        List<User> users = this.list();
        for (User user : users) {
            String role = user.getRole() != null ? user.getRole() : "ROLE_USER";
            user.setRoles(List.of(role));
        }
        return users;
    }

    /* Song：-----------------------内置方法------------------------- */
    // Song：=================== 角色管理 ===================

    @Override
    public void assignRole(String userId, String roleCode) {
        User user = getById(userId);
        if (user == null)
            throw new RuntimeException("用户不存在");
        user.setRole(roleCode);
        updateById(user);
        log.info("为用户 {} 分配角色 {}", userId, roleCode);
    }

    // Song：=================== 资料 方法 ===================

    @Override
    public void addContribution(String userId, int amount) {
        User user = getById(userId);
        if (user == null)
            return;
        user.setContributionVal((user.getContributionVal() != null ? user.getContributionVal() : 0) + amount);
        updateById(user);
    }

    @Override
    public void updateLastActiveTime(String userId) {
        User user = getById(userId);
        if (user == null)
            return;
        user.setLastActiveTime(LocalDateTime.now());
        updateById(user);
    }

    @Override
    public void incrementLikesReceived(String userId) {
        User user = getById(userId);
        if (user == null)
            return;
        user.setTotalLikesReceived((user.getTotalLikesReceived() != null ? user.getTotalLikesReceived() : 0) + 1);
        updateById(user);
    }

    @Override
    public void decrementLikesReceived(String userId) {
        User user = getById(userId);
        if (user == null)
            return;
        user.setTotalLikesReceived(
                Math.max(0, (user.getTotalLikesReceived() != null ? user.getTotalLikesReceived() : 0) - 1));
        updateById(user);
    }

    @Override
    public void incrementTotalPosts(String userId) {
        User user = getById(userId);
        if (user == null)
            return;
        user.setTotalPosts((user.getTotalPosts() != null ? user.getTotalPosts() : 0) + 1);
        updateById(user);
    }

    @Override
    public void updatePreferredSections(String userId, String sectionId) {
        // Song：简化实现：毕设数据量小，暂不做复杂偏好计算
    }

    @Override
    public void updateActiveRegion(String userId, String region) {
        User user = getById(userId);
        if (user == null)
            return;
        user.setActiveRegion(region);
        updateById(user);
    }

}
