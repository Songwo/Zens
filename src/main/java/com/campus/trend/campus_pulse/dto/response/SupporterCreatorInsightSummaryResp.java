package com.campus.trend.campus_pulse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupporterCreatorInsightSummaryResp {
    private long publishedPosts;
    private long totalViews;
    private long totalLikes;
    private long totalCollects;
    private long totalComments;
    private long avgDwellSec;
}
