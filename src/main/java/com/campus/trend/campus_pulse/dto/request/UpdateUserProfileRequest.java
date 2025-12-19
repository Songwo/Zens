package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新用户画像请求DTO
 */
@Data
public class UpdateUserProfileRequest {

    /**
     * 活跃地点
     */
    private String activeRegion;

}
