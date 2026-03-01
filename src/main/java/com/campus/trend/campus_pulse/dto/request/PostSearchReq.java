package com.campus.trend.campus_pulse.dto.request;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PostSearchReq {

    // Song：说明
    private String userId;

    // Song：搜索关键词
    private String keyword;

    // Song：状态
    private Integer status;

    private Integer page = 1;
    private Integer pageSize = 10;

    // Song：说明
    private String orderBy;

    // Song：说明
    private String timeRange;

    // Song：是否只看精华贴
    private Boolean isFeatured;

    // Song：说明
    private Long sectionId;

    // Song：标签筛选
    private String tag;

    // Song：说明
    private String likedBy;

    // Song：说明
    private String collectedBy;

    // Song：是否只看置顶帖
    private Boolean pinnedOnly;

    // Song：说明
    private LocalDateTime cursor;

    // Song：说明
    private String cursorId;
}
