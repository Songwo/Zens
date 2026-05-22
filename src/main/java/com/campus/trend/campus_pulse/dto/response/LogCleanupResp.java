package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

@Data
public class LogCleanupResp {

    private long scannedFiles;
    private long keptFiles;
    private long deletedFiles;
    private long deletedBytes;
}
