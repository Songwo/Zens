package com.campus.trend.campus_pulse.common.exception;

import com.campus.trend.campus_pulse.common.api.ResultCode;
import lombok.Getter;

/**
 * Song：统一业务异常
 */
@Getter
public class BusinessException extends RuntimeException {
    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(String message) {
        super(message);
        this.resultCode = ResultCode.FAILED;
    }
    
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
