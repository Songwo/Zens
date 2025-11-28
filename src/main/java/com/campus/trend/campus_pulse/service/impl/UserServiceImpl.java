package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.dto.response.ProFileResponse;
import com.campus.trend.campus_pulse.dto.response.SimpleProfileResponse;
import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.service.mapperservice.SysUserService;
import com.campus.trend.campus_pulse.utils.GetStringFile;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class UserServiceImpl implements UserService {


    private final SysUserService sysUserService;


    public UserServiceImpl(SysUserService sysUserService) {
        this.sysUserService = sysUserService;

    }

    @Override
    public ProFileResponse getProFile() {
        // 1.从Security上下文中获取用户信息
        AuthSysUser auUser = GetUserDetail.getAuthenticatedUser();

        // 2.获取用户详细信息
        SysUser sysUser = sysUserService.searchByUsername(auUser.getUsername());
        // 3.构造用户信息响应
        ProFileResponse proFileResponse = new ProFileResponse();
        proFileResponse.setUsername(sysUser.getUsername());
        proFileResponse.setAvatar(sysUser.getAvatar());
        proFileResponse.setNickname(sysUser.getNickname());
        proFileResponse.setMajor(sysUser.getMajor());
        proFileResponse.setGrade(sysUser.getGrade());
        proFileResponse.setInterest_tags(sysUser.getInterestTags());
        proFileResponse.setCreatTime(sysUser.getCreateTime());

        return proFileResponse;
    }

    @Override
    public SimpleProfileResponse getSimpleProfile() {
        // 1.从Security上下文中获取用户信息
        AuthSysUser auUser = GetUserDetail.getAuthenticatedUser();

        // 2.获取用户详细信息
        SysUser sysUser = sysUserService.searchByUsername(auUser.getUsername());
        // 3.构造用户信息响应
        SimpleProfileResponse simpleProfileResponse = new SimpleProfileResponse();
        simpleProfileResponse.setAvatar(sysUser.getAvatar());
        simpleProfileResponse.setNickname(sysUser.getNickname());
        simpleProfileResponse.setInterest_tags(sysUser.getInterestTags());

        return simpleProfileResponse;
    }

    @Override
    public String UploadAvatar(MultipartFile file) {

        String suffix = GetStringFile.getString(file);

        // 获取项目根目录
        String rootPath = System.getProperty("user.dir");
        String uploadDir = rootPath + "/data/avatar/";

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

        return "http://localhost:7800/static/avatar/" + fileName;
    }

    @Override
    public List<SysUser> getUsers() {
        return sysUserService.searchAll();
    }

}
