package com.campus.trend.campus_pulse.service;

import java.util.List;
import java.util.Map;

/**
 * AI智能助手服务接口
 */
public interface AiAssistantService {

    /**
     * 查找相似已解决问题
     * @param title 帖子标题
     * @param content 帖子内容
     * @return 相似帖子列表
     */
    List<Map<String, Object>> findSimilarPosts(String title, String content);

    /**
     * 评估内容质量
     * @param title 标题
     * @param content 内容
     * @return 质量评分和建议
     */
    Map<String, Object> evaluateContentQuality(String title, String content);

    /**
     * 智能标签推荐
     * @param title 标题
     * @param content 内容
     * @return 推荐标签列表
     */
    List<String> suggestTags(String title, String content);

    /**
     * 评论摘要生成
     * @param postId 帖子ID
     * @return 评论摘要
     */
    String summarizeComments(String postId);

    /**
     * 敏感内容识别
     * @param content 内容
     * @return 识别结果
     */
    Map<String, Object> detectSensitiveContent(String content);
}
