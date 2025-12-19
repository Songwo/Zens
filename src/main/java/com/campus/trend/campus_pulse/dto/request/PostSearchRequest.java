package com.campus.trend.campus_pulse.dto.request;

import lombok.Data;

@Data
public class PostSearchRequest {

    // 分类ID
    private String categoryID;

    // 用户ID
    private String userID;

    // 搜索关键词
    private String keyword;

    // 状态
    private Integer status;

    private Integer page = 1;
    private Integer pageSize = 10;

    // 排序方式: "new" (默认) 或 "hot"
    private String orderBy;

    // 标签筛选
    private String tag;

    // 筛选我点赞的 (User ID)
    private String likedBy;

    // 筛选我收藏的 (User ID)
    private String collectedBy;
}