package com.campus.trend.campus_pulse.exception.custom;

public class RedisOperationException extends RuntimeException {
    public RedisOperationException(String message) {
        super(message);
    }
}
