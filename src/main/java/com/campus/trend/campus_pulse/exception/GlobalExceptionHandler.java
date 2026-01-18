package com.campus.trend.campus_pulse.exception;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.common.ResultCode;
import com.campus.trend.campus_pulse.exception.definexception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 请求体为空
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<String> handleMissingBodyException(HttpMessageNotReadableException e) {
        log.warn("Request body missing: {}", e.getMessage());
        return Result.error(ResultCode.REQUEST_BODY_MISSING, "请求体内容不能为空");
    }

    // 参数校验失败 (Validated/Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((m1, m2) -> m1 + "; " + m2)
                .orElse("参数校验失败");
        log.warn("Validation failed: {}", message);
        return Result.error(ResultCode.VALIDATE_FAILED, message);
    }

    // 学号已经存在错误
    @ExceptionHandler(UserNameAlreadyExisted.class)
    public Result<String> handleUserNameAlreadyExisted(UserNameAlreadyExisted e) {
        return Result.error(ResultCode.USERNAME_ALREADY_EXISTED, e.getMessage());
    }

    // 注册失败异常
    @ExceptionHandler(RegisterException.class)
    public Result<String> handleRegisterException(RegisterException e) {
        return Result.error(ResultCode.REGISTER_ERROR, e.getMessage());
    }

    // 登陆失败异常
    @ExceptionHandler(LoginException.class)
    public Result<String> handleLoginException(LoginException e) {
        return Result.error(ResultCode.LOGIN_ERROR, e.getMessage());
    }

    // 系统异常
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("Unhandled system exception: ", e);
        // 生产环境不应直接返回 e.getMessage()，避免泄露数据库或服务器信息
        return Result.error(ResultCode.SYSTEM_ERROR, "服务器繁忙，请稍后再试");
    }
}
