package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.request.UpdatePasswordRequest;
import com.campus.trend.campus_pulse.dto.request.UpdateUserDetailRequest;
import com.campus.trend.campus_pulse.dto.response.ProFileResponse;
import com.campus.trend.campus_pulse.dto.response.SimpleProfileResponse;
import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.mapper.SysUserMapper;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.GetRootPath;
import com.campus.trend.campus_pulse.utils.GetStringFile;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
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
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    private final PasswordEncoder passwordEncoder;

    @Value("${Web.AvatarUrl}")
    private String url;

    public UserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public ProFileResponse GetProFile() {
        // 1.从Security上下文中获取用户信息
        AuthSysUser auUser = GetUserDetail.getAuthenticatedUser();

        // 2.获取用户详细信息
        SysUser sysUser = searchByUsername(auUser.getUsername());
        // 3.构造用户信息响应
        ProFileResponse proFileResponse = new ProFileResponse();
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
        proFileResponse.setCreatTime(sysUser.getCreateTime());
        proFileResponse.setUpdateTime(sysUser.getUpdateTime());
        proFileResponse.setInterestTags(sysUser.getInterestTags());

        autoUpgradeGrade(sysUser);

        return proFileResponse;
    }

    @Override
    public SimpleProfileResponse GetSimpleProfile() {
        // 1.从Security上下文中获取用户信息
        AuthSysUser auUser = GetUserDetail.getAuthenticatedUser();

        // 2.获取用户详细信息
        SysUser sysUser = searchByUsername(auUser.getUsername());
        // 3.构造用户信息响应
        SimpleProfileResponse simpleProfileResponse = new SimpleProfileResponse();
        simpleProfileResponse.setId(sysUser.getId());
        simpleProfileResponse.setAvatar(sysUser.getAvatar());
        simpleProfileResponse.setNickname(sysUser.getNickname());

        autoUpgradeGrade(sysUser);

        return simpleProfileResponse;
    }

    @Override
    public String UploadAvatar(MultipartFile file) {

        String suffix = GetStringFile.getString(file);

        String uploadDir = GetRootPath.getRootPath() + "/data/avatar/";

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

        return url + fileName;
    }

    @Override
    public void UpdateUserPassword(UpdatePasswordRequest req) {

        // 1.获取当前用户
        AuthSysUser auUser = GetUserDetail.getAuthenticatedUser();
        SysUser sysUser = searchByUsername(auUser.getUsername());

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
    public void UpdateUserDetails(UpdateUserDetailRequest updateUserDetailRequest) {
        // 1.获取当前用户
        AuthSysUser auUser = GetUserDetail.getAuthenticatedUser();
        SysUser sysUser = searchByUsername(auUser.getUsername());

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
    public SysUser searchByUsername(String username) {
        return lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    @Override
    public List<SysUser> searchByGrade(int grade) {
        return lambdaQuery().ge(SysUser::getGrade, grade).list();
    }

    @Override
    public List<SysUser> GetUsers() {
        return this.list();
    }

    /** -----------------------内置方法------------------------- */
    public void autoUpgradeGrade(SysUser user) {

        LocalDateTime last = user.getLastGradeUpgrade();
        if (last == null) {
            last = user.getCreateTime(); // 没有升级记录则从注册时间算
        }

        // 判断是否满一年
        if (last.plusYears(1).isBefore(LocalDateTime.now())) {
            user.setGrade(user.getGrade() + 1);
            user.setLastGradeUpgrade(LocalDateTime.now());

            updateById(user);
        }
    }

}
