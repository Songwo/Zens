package com.campus.trend.campus_pulse.common.exception;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.*;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.BindException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * Song：全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.servlet.multipart.max-file-size:512MB}")
    private String multipartMaxFileSize = "512MB";

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidException(MethodArgumentNotValidException e) {
        String message = extractBindingErrorMessage(e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList());
        log.warn("Validation failed: {}", message);
        return Result.error(ResultCode.VALIDATE_FAILED, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<String> handleBindException(BindException e) {
        String message = extractBindingErrorMessage(e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList());
        log.warn("Bind validation failed: {}", message);
        return Result.error(ResultCode.VALIDATE_FAILED, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<String> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .reduce((left, right) -> left + "; " + right)
                .orElse("参数校验失败");
        log.warn("Constraint validation failed: {}", message);
        return Result.error(ResultCode.VALIDATE_FAILED, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<String> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String message = "缺少必要参数: " + e.getParameterName();
        log.warn("Missing request parameter: {}", message);
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<String> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String message = "参数类型错误: " + e.getName();
        log.warn("Request parameter type mismatch: {}", message);
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<String> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR, e.getMessage());
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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<String> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过限制: {}", e.getMessage());
        return Result.error(ResultCode.FILE_FORMAT_ERROR, "上传文件过大，单个文件不能超过 " + multipartMaxFileSize);
    }

    @ExceptionHandler(MultipartException.class)
    public Result<String> handleMultipartException(MultipartException e) {
        String message = e.getMessage();
        if (message != null && (message.contains("SizeLimitExceededException")
                || message.toLowerCase().contains("size")
                || message.toLowerCase().contains("exceed"))) {
            log.warn("上传文件超过限制: {}", message);
            return Result.error(ResultCode.FILE_FORMAT_ERROR, "上传文件过大，单个文件不能超过 " + multipartMaxFileSize);
        }
        log.warn("Multipart 请求解析失败: {}", message);
        return Result.error(ResultCode.PARAM_ERROR, "上传请求解析失败，请检查文件大小和网络后重试");
    }

    // Song：捕获客户端主动断开连接引起的异常（仅限连接中止，不捕获业务IO异常）
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(Exception e) {
        log.debug("异步请求不可用（客户端可能已断开）: {}", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        String className = e.getClass().getName();
        // Song：客户端主动断开连接（Tomcat ClientAbortException），无需返回响应体
        if (className.contains("ClientAbortException")
                || (e.getMessage() != null && e.getMessage().contains("中止了一个已建立的连接"))) {
            log.debug("客户端主动断开连接: {}", e.getMessage());
            return Result.error(ResultCode.SYSTEM_ERROR, "连接已断开");
        }
        log.error("系统未知异常: ", e);
        return Result.error(ResultCode.SYSTEM_ERROR, "服务器繁忙，请稍后再试");
    }

    private String extractBindingErrorMessage(java.util.List<String> fieldMessages) {
        return fieldMessages.stream()
                .reduce((m1, m2) -> m1 + "; " + m2)
                .orElse("参数校验失败");
    }
}
