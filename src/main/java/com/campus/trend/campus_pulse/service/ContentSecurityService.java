package com.campus.trend.campus_pulse.service;

import java.util.Set;

/**
 * Song：内容安全服务接口
 */
public interface ContentSecurityService {

    /**
     * Song：判断文本是否包含敏感词
     * 
     * Song：说明
     * Song：说明
     */
    boolean containsSensitiveWords(String text);

    /**
     * Song：获取文本中包含的所有敏感词
     * 
     * Song：说明
     * Song：说明
     */
    Set<String> getSensitiveWords(String text);

    /**
     * Song：过滤敏感词，将敏感词替换为 *
     * 
     * Song：说明
     * Song：说明
     */
    String filterSensitiveWords(String text);

    /**
     * Song：添加敏感词（支持动态更新）
     * 
     * Song：说明
     */
    void addSensitiveWords(Set<String> words);

    /**
     * Song：重新加载敏感词库
     */
    void reloadSensitiveWords();

}
