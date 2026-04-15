package com.campus.trend.campus_pulse.dto.request;

import lombok.Data;

/**
 * 草稿保存请求，不强制完整字段，支持未完成内容的多次暂存。
 */
@Data
public class PostDraftReq {
    private String postId;
    private Long sectionId;
    private String title;
    private String content;
    private String images;
    private String coverImage;
    private String tags;
    private Integer isAnonymous;
    private String locationName;
}
