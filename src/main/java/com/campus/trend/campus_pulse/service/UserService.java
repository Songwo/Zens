package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.ProFileResponse;
import com.campus.trend.campus_pulse.dto.response.SimpleProfileResponse;
import com.campus.trend.campus_pulse.entity.SysUser;

import java.util.List;

public interface UserService {

    ProFileResponse getProFile();

    SimpleProfileResponse getSimpleProfile();

    List<SysUser> getUsers();
}
