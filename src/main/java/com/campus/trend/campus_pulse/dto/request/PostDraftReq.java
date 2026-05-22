package com.campus.trend.campus_pulse.dto.request;

import com.campus.trend.campus_pulse.dto.media.MediaObject;
import lombok.Data;

import java.util.List;

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
    /** 新版媒体字段；覆盖式写 sys_post_media。 */
    private List<MediaObject> mediaList;
    private String coverImage;
    private String tags;
    private Integer isAnonymous;
    private String locationName;
}
