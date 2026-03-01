package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.LoginReq;
import com.campus.trend.campus_pulse.dto.request.GithubLoginReq;
import com.campus.trend.campus_pulse.dto.request.RegisterReq;
import com.campus.trend.campus_pulse.dto.response.LoginResponse;
import com.campus.trend.campus_pulse.dto.response.TwoFactorSetupResp;

public interface AuthService {

    LoginResponse login(LoginReq loginRequest, String deviceId, String clientIp);

    LoginResponse refresh(String refreshToken, String deviceId, String clientIp);

    String buildGithubAuthorizeUrl();

    LoginResponse githubLogin(GithubLoginReq req, String deviceId, String clientIp);

    LoginResponse verifyTwoFactorLogin(String ticket, String code, String deviceId, String clientIp);

    TwoFactorSetupResp initTwoFactorSetup(String userId);

    void enableTwoFactor(String userId, String code);

    void disableTwoFactor(String userId, String code);

    void register(RegisterReq registerRequest);

    void logout();

    void logoutByAccessToken(String accessToken);

    /**
     * 忘记密码 — 通过邮箱验证码重置密码
     */
    void resetPassword(String email, String code, String newPassword);

    // TODO 待实现列表
    // 1.用户获取详细个人信息 ✔
    // 2.获取简单个人信息 ✔
    // 3.用户头像上传 ✔
    // 4.根据用户ID改用户密码 ✔
    // 5.根据用户名修改用户信息 ✔
    // 6.年级设置一年自动更新 ✔
    // 7.退出登录 ✔

}
