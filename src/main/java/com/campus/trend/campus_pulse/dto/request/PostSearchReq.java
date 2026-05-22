package com.campus.trend.campus_pulse.dto.request;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostSearchReq {

    private String userId;

    // Song：搜索关键词
    private String keyword;

    // Song：状态
    private Integer status;

    private Integer page = 1;
    private Integer pageSize = 10;

    private String orderBy;

    // latest / hot / essence：文档列表左侧导航条件
    private String navType;

    // 顶部分类筛选，all 表示全部；数字值对应 sectionId
    private String category;

    private String timeRange;

    // Song：是否只看精华贴
    private Boolean isFeatured;

    private Long sectionId;

    // Song：多板块范围过滤（用于版务场景）
    private List<Long> sectionIds;

    // Song：标签筛选
    private String tag;

    private String likedBy;

    private String collectedBy;

    // Song：是否只看置顶帖
    private Boolean pinnedOnly;

    private LocalDateTime cursor;

    private String cursorId;

    // 是否需要分页总数，首屏流式列表默认不需要 count(*)
    private Boolean needTotal;

    // Song：审核状态筛选（PENDING/APPROVED/REJECTED）
    private String auditStatus;

    // Song：内部字段 - 按作者用户名/昵称匹配到的用户ID列表（不从请求体传入）
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient List<String> matchedAuthorIds;

    // 内部字段：后台内容管理可查看草稿、打回、删除等治理状态；不接受前端直接传入。
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient Boolean moderationView;

    // 内部字段：是否包含软删除内容；前台回收站通过 auditStatus=DELETED 精确查询。
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient Boolean includeDeleted;
}
