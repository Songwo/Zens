package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.TagsExtractReq;

import java.util.Map;

/**
 * Song：帖子 智能 分析模块（独立服务层）
 * Song：负责内容清洗、参数边界控制、调用模型服务并返回结构化结果。
 */
public interface AiPostAnalysisService {

    /**
     * Song：提取帖子标签和摘要
     *
     */
    Map<String, Object> extractTagsAndSummary(TagsExtractReq request);
}
