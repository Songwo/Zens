package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.request.UserPasswordUpdateReq;
import com.campus.trend.campus_pulse.dto.request.UserDetailUpdateReq;
import com.campus.trend.campus_pulse.dto.response.UserDetailResp;
import com.campus.trend.campus_pulse.dto.response.UserProfileResp;
import com.campus.trend.campus_pulse.dto.response.UserSearchItemResp;
import com.campus.trend.campus_pulse.dto.response.UserSimpleResp;
import com.campus.trend.campus_pulse.dto.response.UserStatsResp;
import com.campus.trend.campus_pulse.entity.User;

import java.util.List;

public interface UserService extends IService<User> {

    UserDetailResp getProfile();

    UserSimpleResp getSimpleProfile();

    /** 公开主页（含帖子/关注/粉丝计数），用户不存在或被封禁时返回 null */
    UserProfileResp getPublicProfile(String userId);

    List<UserSearchItemResp> searchUsers(String keyword);

    UserStatsResp getProfileStats(String userId);

    List<User> getUsers();

    String updateAvatar(String avatarUrl);

    void updateUserPassword(UserPasswordUpdateReq updatePasswordRequest);

    void updateUserDetails(UserDetailUpdateReq updateUserDetailRequest);

    User searchByUsername(String username);

    List<User> searchByGrade(int grade);

    // =================== 角色管理 ===================
    void assignRole(String userId, String roleCode);

    void updateModeratedSections(String userId, List<Long> sectionIds);

    // =================== Profile 方法 (原 UserProfileService) ===================
    void addContribution(String userId, int amount);
    void updateLastActiveTime(String userId);
    void incrementLikesReceived(String userId);
    void decrementLikesReceived(String userId);
    void incrementTotalPosts(String userId);
    void updatePreferredSections(String userId, String sectionId);
    void updateActiveRegion(String userId, String region);
}
