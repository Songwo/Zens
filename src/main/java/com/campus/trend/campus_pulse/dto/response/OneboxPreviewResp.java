package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Onebox 链接预览响应（抓取目标页 OG meta 后的结构化结果）
 */
@Data
@Accessors(chain = true)
public class OneboxPreviewResp {

    /** 原始 URL */
    private String url;

    /** 标题（og:title / title） */
    private String title;

    /** 描述（og:description / meta description） */
    private String description;

    /** 预览图（og:image） */
    private String image;

    /** 站点来源（og:site_name / hostname） */
    private String siteName;

    /** 识别出的 provider：github/youtube/bilibili/twitter/generic */
    private String provider;

    /** 嵌入用 ID（如 YouTube videoId、Bilibili bvid），generic 为 null */
    private String embedId;

    /** 是否可嵌入（YouTube/Bilibili 返回 true） */
    private boolean embeddable;

    /** 抓取是否命中缓存 */
    private boolean cached;
}
