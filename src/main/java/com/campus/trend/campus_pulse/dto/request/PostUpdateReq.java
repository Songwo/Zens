package com.campus.trend.campus_pulse.dto.request;

import com.campus.trend.campus_pulse.dto.media.MediaObject;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PostUpdateReq {
    private String postId;

    @Size(min = 4, max = 100, message = "文章题目需超过3个字符且不超过100个字符")
    private String title;

    @Size(min = 31, message = "文章内容需超过30个字符")
    private String content;
    private String coverImage;
    private String images; // Song：说明（旧字段，保留兼容）
    /** 新版媒体字段：完整 MediaObject 列表；覆盖式更新。 */
    private List<MediaObject> mediaList;
    private String tags; // Song：说明
    private Long sectionId;
    private Integer isAnonymous;
    private String locationName;
    private Integer status;
    private Boolean publish;
}
