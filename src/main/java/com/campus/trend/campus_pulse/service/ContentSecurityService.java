package com.campus.trend.campus_pulse.service;

import java.util.Set;

/**
 * 内容安全服务接口
 */
public interface ContentSecurityService {

    /**
     * 判断文本是否包含敏感词
     * 
     * @param text 待检测文本
     * @return true=包含, false=不包含
     */
    boolean containsSensitiveWords(String text);

    /**
     * 获取文本中包含的所有敏感词
     * 
     * @param text 待检测文本
     * @return 敏感词集合
     */
    Set<String> getSensitiveWords(String text);

    /**
     * 过滤敏感词，将敏感词替换为 *
     * 
     * @param text 原始文本
     * @return 过滤后的文本
     */
    String filterSensitiveWords(String text);

    /**
     * 添加敏感词（支持动态更新）
     * 
     * @param words 敏感词列表
     */
    void addSensitiveWords(Set<String> words);

    /**
     * 重新加载敏感词库
     */
    void reloadSensitiveWords();

}
