package com.campus.trend.campus_pulse.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class UserModeratedSectionsUpdateReq {
    private List<Long> sectionIds;
}
