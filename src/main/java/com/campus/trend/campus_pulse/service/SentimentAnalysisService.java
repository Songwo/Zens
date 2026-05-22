package com.campus.trend.campus_pulse.service;

/**
 * Song：情感分析服务接口
 */
public interface SentimentAnalysisService {

    /**
     * Song：分析文本情感得分
     * 
     */
    double analyzeSentiment(String text);

    /**
     * Song：获取情感标签
     * 
     */
    String getSentimentLabel(double score);

}
