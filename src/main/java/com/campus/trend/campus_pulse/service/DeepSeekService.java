package com.campus.trend.campus_pulse.service;

import java.util.Map;

public interface DeepSeekService {

    /**
     * Song：从文本中提取标签和摘要
     * 
     */
    Map<String, Object> extractTagsAndSummary(String content, int tagSize, int summarySize);
}
