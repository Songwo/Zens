package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CoverConfigUpdateReq {

    @Pattern(regexp = "^(cover|contain)$", message = "fit 仅支持 cover/contain")
    private String fit;

    private Integer x;
    private Integer y;
    private Integer height;
}
