package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.service.SentimentAnalysisService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 情感分析服务实现类
 * 使用基于词典的简单情感分析算法
 */
@Service
@Slf4j
public class SentimentAnalysisServiceImpl implements SentimentAnalysisService {

    private Set<String> positiveWords;
    private Set<String> negativeWords;

    @PostConstruct
    public void init() {
        positiveWords = loadWords("analysis/positive_words.txt");
        negativeWords = loadWords("analysis/negative_words.txt");

        // 如果文件不存在，加载默认测试词典
        if (positiveWords.isEmpty()) {
            positiveWords.add("开心");
            positiveWords.add("快乐");
            positiveWords.add("喜欢");
            positiveWords.add("棒");
            positiveWords.add("优秀");
            positiveWords.add("成功");
            positiveWords.add("感谢");
            positiveWords.add("美好");
        }

        if (negativeWords.isEmpty()) {
            negativeWords.add("难过");
            negativeWords.add("悲伤");
            negativeWords.add("讨厌");
            negativeWords.add("差");
            negativeWords.add("失败");
            negativeWords.add("失望");
            negativeWords.add("痛苦");
            negativeWords.add("糟糕");
        }

        log.info("情感分析词典加载完成. 正面词: {}, 负面词: {}", positiveWords.size(), negativeWords.size());
    }

    @Override
    public double analyzeSentiment(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.5; // 中性
        }

        int positiveCount = 0;
        int negativeCount = 0;

        // 简单统计词频
        // 优化点：可以使用分词工具(如HanLP/Jieba)分词后再匹配，这里简化为字符串包含匹配
        for (String word : positiveWords) {
            if (text.contains(word)) {
                positiveCount++;
            }
        }

        for (String word : negativeWords) {
            if (text.contains(word)) {
                negativeCount++;
            }
        }

        if (positiveCount == 0 && negativeCount == 0) {
            return 0.5;
        }

        // 简单计算公式
        double total = positiveCount + negativeCount;
        double score = 0.5 + (0.5 * (positiveCount - negativeCount) / total);

        // 归一化到 0.0 - 1.0
        return Math.max(0.0, Math.min(1.0, score));
    }

    @Override
    public String getSentimentLabel(double score) {
        if (score > 0.6) {
            return "POSITIVE";
        } else if (score < 0.4) {
            return "NEGATIVE";
        } else {
            return "NEUTRAL";
        }
    }

    private Set<String> loadWords(String filename) {
        Set<String> words = new HashSet<>();
        try {
            ClassPathResource resource = new ClassPathResource(filename);
            if (resource.exists()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            words.add(line.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("加载情感词典文件 {} 失败", filename);
        }
        return words;
    }
}
