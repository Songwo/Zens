package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

@Data
public class UserSimpleResp {

    private String id;
    private String username;
    private String nickname;
    private String avatar;
    private String interestTags;
    private Integer level;
    private Integer points;

}
