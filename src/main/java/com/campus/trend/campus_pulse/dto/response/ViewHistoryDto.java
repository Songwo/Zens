package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ViewHistoryDto {
    private String postId;
    private String title;
    private LocalDateTime viewTime;
}
