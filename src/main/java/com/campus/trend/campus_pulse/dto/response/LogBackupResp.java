package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

@Data
public class LogBackupResp {

    private String relativePath;
    private String fileName;
    private long sizeBytes;
    private String createdTime;
    private boolean compressed;
}
