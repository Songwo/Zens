package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

@Data
public class LogContentResp {

    private String relativePath;
    private String fileName;
    private String mode;
    private int lineCount;
    private int maxLines;
    private int maxBytes;
    private boolean truncated;
    private boolean compressed;
    private long sizeBytes;
    private String modifiedTime;
    private String content;
}
