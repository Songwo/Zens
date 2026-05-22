package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

@Data
public class LogFileSummaryResp {

    private String relativePath;
    private String fileName;
    private String type;
    private boolean active;
    private boolean compressed;
    private boolean previewTailSupported;
    private boolean downloadable;
    private boolean deletable;
    private long sizeBytes;
    private String modifiedTime;
}
