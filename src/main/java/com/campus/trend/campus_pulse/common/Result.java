package com.campus.trend.campus_pulse.common;

import lombok.Data;

@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    //成功
    public static  <T> Result<T> success(){
        return build(null,ResultCode.SUCCESS);
    }

    //成功（带参数）
    public static  <T> Result<T> success(T data){
        return build(data,ResultCode.SUCCESS);
    }

    //失败
    public static <T> Result<T> error(ResultCode resultCode){
        return build(null,resultCode);
    }

    //失败（自定义消息）
    public static <T> Result<T> error(ResultCode resultCode,String message){
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMessage(message);
        return result;
    }

    //内置方法，基础！！
    private static <T> Result<T> build(T data,ResultCode codeEnum){
        Result<T> result = new Result<T>();
        result.setCode(codeEnum.getCode());
        result.setMessage(codeEnum.getMessage());
        result.setData(data);
        return result;
    }

}
