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

    private Integer page;
    private Integer pageSize;

    // 排序方式: "new" (默认) 或 "hot"
    private String orderBy;
}