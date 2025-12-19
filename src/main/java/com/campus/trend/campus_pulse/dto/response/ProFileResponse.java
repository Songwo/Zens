package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProFileResponse {

    private String id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private String major;
    private int grade;
    private String school;
    private Integer gender;
    private Integer role;
    private Integer status;
    private String interest_tags;
    private LocalDateTime CreatTime;
    private LocalDateTime UpdateTime;

}
