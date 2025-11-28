package com.campus.trend.campus_pulse.exception;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.common.ResultCode;
import com.campus.trend.campus_pulse.exception.definexception.*;
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

    //学号已经存在错误
    @ExceptionHandler(UserNameAlreadyExisted.class)
    public Result<String> handleUserNameAlreadyExisted(UserNameAlreadyExisted e){
        String message = e.getMessage();
        return Result.error(ResultCode.USERNAME_ALREADY_EXISTED,message);
    }

    //注册失败异常
    @ExceptionHandler(RegisterException.class)
    public Result<String> handleRegisterException(RegisterException e){
        String message = e.getMessage();
        return Result.error(ResultCode.REGISTER_ERROR,message);
    }

    //登陆失败异常
    @ExceptionHandler(LoginException.class)
    public Result<String> handleLoginException(LoginException e){
        String message = e.getMessage();
        return Result.error(ResultCode.LOGIN_ERROR,message);
    }

    //退出登录异常
    @ExceptionHandler(RedisDeleteException.class)
    public Result<String> handleRedisDeleteException(RedisDeleteException e){
        String message = e.getMessage();
        return Result.error(ResultCode.REDIS_DELETE_ERROR,message);
    }

    //文件为空
    @ExceptionHandler(FileIsNullException.class)
    public Result<String> handleFileIsNull(FileIsNullException e){
        String message = e.getMessage();
        return Result.error(ResultCode.FILE_IS_NULL,message);
    }

    //文件名字不合法异常
    @ExceptionHandler(FileNameFailException.class)
    public Result<String> handleFileNameFail(FileNameFailException e){
        String message = e.getMessage();
        return Result.error(ResultCode.FILE_NAME_FAIL,message);
    }

    //文件格式不正确异常
    @ExceptionHandler(FileGeShiException.class)
    public Result<String> handleFileGeShi(FileGeShiException e){
        String message = e.getMessage();
        return Result.error(ResultCode.FILE_GESHI,message);
    }

    //系统异常
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e){
        return Result.error(ResultCode.SYSTEM_ERROR,e.getMessage());
    }



}
