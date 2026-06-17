package com.campus.trend.campus_pulse.handler;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 全局异常处理器（优化版）
 * 统一异常响应格式，记录异常日志，保护敏感信息
 */
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {} | URI: {} | Message: {}",
            e.getResultCode().getCode(), request.getRequestURI(), e.getMessage());
        return Result.error(e.getResultCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String errors = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));

        log.warn("参数校验失败: URI: {} | Errors: {}", request.getRequestURI(), errors);
        return Result.error(ResultCode.VALIDATE_FAILED, "参数校验失败: " + errors);
    }

    /**
     * 参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBindException(BindException e, HttpServletRequest request) {
        String errors = e.getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));

        log.warn("参数绑定失败: URI: {} | Errors: {}", request.getRequestURI(), errors);
        return Result.error(ResultCode.PARAM_ERROR, "参数错误: " + errors);
    }

    /**
     * 参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("参数类型错误: URI: {} | Param: {} | RequiredType: {}",
            request.getRequestURI(), e.getName(), e.getRequiredType());
        return Result.error(ResultCode.PARAM_ERROR,
            String.format("参数 %s 类型错误", e.getName()));
    }

    /**
     * 空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleNullPointerException(NullPointerException e, HttpServletRequest request) {
        log.error("空指针异常: URI: {} | Error: {}", request.getRequestURI(), e.getMessage(), e);
        return Result.error(ResultCode.SYSTEM_ERROR, "系统内部错误");
    }

    /**
     * 非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("非法参数: URI: {} | Error: {}", request.getRequestURI(), e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR, e.getMessage());
    }

    /**
     * 非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleIllegalStateException(IllegalStateException e, HttpServletRequest request) {
        log.warn("非法状态: URI: {} | Error: {}", request.getRequestURI(), e.getMessage());
        return Result.error(ResultCode.FAILED, e.getMessage());
    }

    /**
     * 通用异常（兜底）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("未处理异常: URI: {} | Type: {} | Error: {}",
            request.getRequestURI(), e.getClass().getName(), e.getMessage(), e);

        // 生产环境隐藏详细错误信息
        return Result.error(ResultCode.SYSTEM_ERROR, "系统异常，请稍后再试");
    }
}
