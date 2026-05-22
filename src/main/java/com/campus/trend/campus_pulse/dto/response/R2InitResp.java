package com.campus.trend.campus_pulse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class R2InitResp {

    private String mode;
    private String fileKey;
    private String accessUrl;
    private String uploadUrl;
    private String uploadId;
    private Integer partCount;
    private Integer partSizeBytes;
    private String cacheControl;
    private List<R2PartUploadResp> parts;
    private String fileId;
}
