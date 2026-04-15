package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserDetailResp {

    private String id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private String bio;
    private String major;
    private Integer level;
    private int enrollmentYear;
    private String school;
    private Integer gender;
    private Integer status;
    private String interestTags;
    private Integer twoFactorEnabled;
    private Integer emailNotifyEnabled;
    private Boolean githubBound;
    private String profileCardTheme;
    private String quickCardTheme;
    private String profileCardBgUrl;
    private String quickCardBgUrl;
    private List<Long> moderatedSectionIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * Song：用户角色列表（角色代码）
     */
    private List<String> roles;

}
