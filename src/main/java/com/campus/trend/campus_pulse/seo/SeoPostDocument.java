package com.campus.trend.campus_pulse.seo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SeoPostDocument {
    private String id;
    private String title;
    private String summary;
    private String content;
    private String coverImage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
