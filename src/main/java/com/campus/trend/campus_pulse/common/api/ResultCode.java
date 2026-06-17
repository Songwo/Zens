package com.campus.trend.campus_pulse.common.api;

import lombok.Getter;

/**
 * Song：业务状态码
 */
@Getter
public enum ResultCode {

    // Song：2000开头：通用成功/失败
    SUCCESS(2000, "操作成功"),
    FAILED(2001, "操作失败"),

    // Song：3000开头：用户与权限
    LOGIN_ERROR(3001, "用户名或密码错误"),
    LOGIN_EXPIRED(3002, "登录信息已过期"),
    NO_PERMISSION(3003, "无访问权限"),
    TOKEN_MISSING(3004, "未提供Token"),
    TOKEN_INVALID(3005, "Token无效"),
    REGISTER_ERROR(3006,"用户注册失败"),
    REDIS_DELETE_ERROR(3007,"Redis 登录信息删除失败"),

    // Song：4000开头：参数错误
    PARAM_ERROR(4001, "参数错误"),
    REQUEST_BODY_MISSING(4002, "请求体为空"),
    VALIDATE_FAILED(4003, "参数校验失败"),
    TOO_MANY_REQUESTS(4029, "请求过于频繁，请稍后再试"),

    // Song：5000开头：资源问题
    USER_NOT_FOUND(5001, "用户不存在"),
    POST_NOT_FOUND(5002, "帖子不存在"),
    SECTION_NOT_FOUND(5007, "板块不存在"),
    DRAFT_NOT_FOUND(5008, "草稿不存在"),
    USERNAME_ALREADY_EXISTED(5003,"用户已经存在"),
    FILE_IS_NULL(5004,"文件为空"),
    FILE_NAME_FAIL(5005,"文件名不合法"),
    FILE_FORMAT_ERROR(5006,"文件格式不属于jpg、png、jpeg"),

    // Song：9000开头：系统异常
    SYSTEM_ERROR(9000, "系统异常，请稍后再试");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
