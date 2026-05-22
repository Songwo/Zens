package com.campus.trend.campus_pulse.dto.response;

import java.util.List;

/** 经验记录分页响应，对齐 MyBatis-Plus IPage 字段：records/total/current/size/pages。 */
public record LevelExpRecordPageResp(
        List<LevelExpRecordResp> records,
        long total,
        long current,
        long size,
        long pages
) {

    public static LevelExpRecordPageResp empty(int page, int pageSize) {
        return new LevelExpRecordPageResp(List.of(), 0L, page, pageSize, 0L);
    }
}
