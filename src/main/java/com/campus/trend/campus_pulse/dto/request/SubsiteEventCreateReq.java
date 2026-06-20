package com.campus.trend.campus_pulse.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubsiteEventCreateReq {

    @NotBlank
    @Size(min = 8, max = 120)
    @Pattern(regexp = "^[a-z0-9._:-]+$", message = "eventId 只能包含小写字母、数字、点、下划线、冒号或短横线")
    private String eventId;

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[a-z0-9._-]+$", message = "source 只能包含小写字母、数字、点、下划线或短横线")
    private String source;

    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = "^[a-z0-9._:-]+$", message = "eventType 只能包含小写字母、数字、点、下划线、冒号或短横线")
    private String eventType;

    @Size(max = 64)
    private String userId;

    @NotBlank
    @Size(max = 120)
    private String title;

    @NotBlank
    @Size(max = 500)
    private String content;

    @Size(max = 200)
    private String relatedId;

    @Size(max = 20)
    @Pattern(regexp = "^(info|success|warning|danger|error|default)?$", message = "severity 不合法")
    private String severity;

    @Size(max = 30)
    @Pattern(regexp = "^[a-z0-9._:-]*$", message = "status 只能包含小写字母、数字、点、下划线、冒号或短横线")
    private String status;

    private Boolean notifyUser;

    private JsonNode payload;
}
