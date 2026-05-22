package com.campus.trend.campus_pulse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class R2PrecheckResp {

    private boolean hit;
    private String accessUrl;
    private String fileId;
}
