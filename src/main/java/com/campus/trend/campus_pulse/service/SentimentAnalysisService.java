package com.campus.trend.campus_pulse.service;

/**
 * 情感分析服务接口
 */
public interface SentimentAnalysisService {

    /**
     * 分析文本情感得分
     * 
     * @param text 待分析文本
     * @return 情感得分 (0.0-1.0), 0.5为中性, >0.5为正面, <0.5为负面
     */
    double analyzeSentiment(String text);

    /**
     * 获取情感标签
     * 
     * @param score 情感得分
     * @return "POSITIVE", "NEUTRAL", "NEGATIVE"
     */
    String getSentimentLabel(double score);

}
