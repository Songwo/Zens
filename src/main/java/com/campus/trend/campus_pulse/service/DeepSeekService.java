package com.campus.trend.campus_pulse.service;

import java.util.Map;

/**
 * Song：说明
 */
public interface DeepSeekService {

    /**
     * Song：从文本中提取标签和摘要
     * 
     * Song：说明
     * Song：说明
     * Song：说明
     * Song：说明
     */
    Map<String, Object> extractTagsAndSummary(String content, int tagSize, int summarySize);
}
