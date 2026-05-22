package com.campus.trend.campus_pulse.dto.response;

import java.util.List;

/** 简易分页响应（适用于内存分页场景，不使用 MyBatis IPage 的场合）。 */
public record SimplePageResp<T>(List<T> records, long total) {

    public static <T> SimplePageResp<T> of(List<T> records, long total) {
        return new SimplePageResp<>(records, total);
    }
}
