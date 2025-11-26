package com.campus.trend.campus_pulse.exception;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.common.ResultCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    //请求体为空
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<String> handleMissingBodyException(HttpMessageNotReadableException e){
        return Result.error(ResultCode.REQUEST_BODY_MISSING,e.getMessage());
    }

    //参数错误
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValid(MethodArgumentNotValidException e){
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return Result.error(ResultCode.VALIDATE_FAILED,message);
    }

    //系统异常
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e){
        return Result.error(ResultCode.SYSTEM_ERROR,e.getMessage());
    }


}
