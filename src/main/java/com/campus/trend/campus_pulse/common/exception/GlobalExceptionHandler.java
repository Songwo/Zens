package com.campus.trend.campus_pulse.common.exception;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.exception.custom.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Song：全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getResultCode(), e.getMessage());
    }

    // Song：请求体为空
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<String> handleMissingBodyException(HttpMessageNotReadableException e) {
        log.warn("Request body missing: {}", e.getMessage());
        return Result.error(ResultCode.REQUEST_BODY_MISSING, "请求体内容不能为空");
    }

    // Song：说明
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((m1, m2) -> m1 + "; " + m2)
                .orElse("参数校验失败");
        log.warn("Validation failed: {}", message);
        return Result.error(ResultCode.VALIDATE_FAILED, message);
    }

    // Song：学号已经存在错误
    @ExceptionHandler(UserAlreadyExistsException.class)
    public Result<String> handleUserAlreadyExistsException(UserAlreadyExistsException e) {
        return Result.error(ResultCode.USERNAME_ALREADY_EXISTED, e.getMessage());
    }

    // Song：注册失败异常
    @ExceptionHandler(RegisterException.class)
    public Result<String> handleRegisterException(RegisterException e) {
        return Result.error(ResultCode.REGISTER_ERROR, e.getMessage());
    }

    // Song：登陆失败异常
    @ExceptionHandler(LoginException.class)
    public Result<String> handleLoginException(LoginException e) {
        return Result.error(ResultCode.LOGIN_ERROR, e.getMessage());
    }

    // Song：文件格式错误
    @ExceptionHandler(FileFormatException.class)
    public Result<String> handleFileFormatException(FileFormatException e) {
        return Result.error(ResultCode.FILE_FORMAT_ERROR, e.getMessage());
    }

    // Song：文件为空
    @ExceptionHandler(FileEmptyException.class)
    public Result<String> handleFileEmptyException(FileEmptyException e) {
        return Result.error(ResultCode.FILE_IS_NULL, e.getMessage());
    }

    // Song：文件名错误
    @ExceptionHandler(InvalidFileNameException.class)
    public Result<String> handleInvalidFileNameException(InvalidFileNameException e) {
        return Result.error(ResultCode.FILE_NAME_FAIL, e.getMessage());
    }

    // Song：捕获前端或者客户端主动断开连接引起的异常
    @ExceptionHandler({ org.springframework.web.context.request.async.AsyncRequestNotUsableException.class,
            java.io.IOException.class })
    public void handleClientAbortException(Exception e) {
        if (e.getClass().getName().contains("ClientAbortException") ||
                e.getMessage() != null && e.getMessage().contains("中止了一个已建立的连接")) {
            log.warn("客户端中止了连接，通常因为请求超时或用户关闭了页面: {}", e.getMessage());
        } else {
            log.error("IO异常或断开连接异常: ", e);
        }
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        if (e.getClass().getName().contains("ClientAbortException")) {
            log.warn("客户端主动断开连接: {}", e.getMessage());
            return null;
        }
        log.error("系统未知异常: ", e);
        return Result.error(ResultCode.SYSTEM_ERROR, "服务器繁忙，请稍后再试");
    }
}
