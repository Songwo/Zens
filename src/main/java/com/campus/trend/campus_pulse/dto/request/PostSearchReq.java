package com.campus.trend.campus_pulse.dto.request;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

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

    // Song：多板块范围过滤（用于版务场景）
    private List<Long> sectionIds;

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

    // 是否需要分页总数，首屏流式列表默认不需要 count(*)
    private Boolean needTotal;

    // Song：审核状态筛选（PENDING/APPROVED/REJECTED）
    private String auditStatus;
}
