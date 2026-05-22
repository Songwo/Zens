package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.service.ContentSecurityService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Song：内容安全服务实现类
 */
@Service
@Slf4j
public class ContentSecurityServiceImpl implements ContentSecurityService {

    // Song：敏感词树根节点
    private Map<Object, Object> sensitiveWordMap;

    // Song：最小匹配规则 (匹配到词就认为是敏感词，如：中国、中国人，匹配到中国返回)
    public static final int minMatchTYpe = 1;
    // Song：最大匹配规则 (如：中国、中国人，匹配到中国人返回)
    public static final int maxMatchType = 2;

    @PostConstruct
    public void init() {
        log.info("开始初始化敏感词库...");
        reloadSensitiveWords();
    }

    @Override
    public boolean containsSensitiveWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        Set<String> sensitiveWords = getSensitiveWords(text, minMatchTYpe);
        return !sensitiveWords.isEmpty();
    }

    @Override
    public Set<String> getSensitiveWords(String text) {
        return getSensitiveWords(text, maxMatchType);
    }

    @Override
    public String filterSensitiveWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        Set<String> sensitiveWords = getSensitiveWords(text, maxMatchType);
        Iterator<String> iterator = sensitiveWords.iterator();
        String resultText = text;
        while (iterator.hasNext()) {
            String word = iterator.next();
            String replaceString = getReplaceChars("*", word.length());
            resultText = resultText.replaceAll(word, replaceString);
        }
        return resultText;
    }

    @Override
    public void addSensitiveWords(Set<String> words) {
        if (words == null || words.isEmpty()) {
            return;
        }
        if (sensitiveWordMap == null) {
            sensitiveWordMap = new HashMap<>();
        }
        for (String word : words) {
            addWordToMap(word);
        }
    }

    @Override
    public void reloadSensitiveWords() {
        Set<String> words = loadSensitiveWordsFromFile();
        // Song：也可以从数据库加载

        Map<Object, Object> newMap = new HashMap<>(words.size());
        for (String word : words) {
            addWordToMap(word, newMap);
        }
        this.sensitiveWordMap = newMap;
        log.info("敏感词库初始化完成，共加载 {} 个敏感词", words.size());
    }

    /**
     * Song：从文件加载默认敏感词
     */
    private Set<String> loadSensitiveWordsFromFile() {
        Set<String> words = new HashSet<>();
        try {
            ClassPathResource resource = new ClassPathResource("analysis/sensitive_words.txt");
            if (!resource.exists()) {
                log.warn("敏感词字典文件 analysis/sensitive_words.txt 不存在");
                // Song：添加一些默认测试词
                words.add("笨蛋");
                words.add("傻瓜");
                words.add("作弊");
                return words;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty() && !line.startsWith("#")) {
                        words.add(line.trim());
                    }
                }
            }
        } catch (Exception e) {
            log.error("加载敏感词文件失败", e);
        }
        return words;
    }

    private void addWordToMap(String word, Map<Object, Object> map) {
        Map<Object, Object> nowMap = map;
        for (int i = 0; i < word.length(); i++) {
            char keyChar = word.charAt(i);
            Object wordMap = nowMap.get(keyChar);

            if (wordMap != null) {
                nowMap = (Map<Object, Object>) wordMap;
            } else {
                Map<Object, Object> newWorMap = new HashMap<>();
                newWorMap.put("isEnd", "0");
                nowMap.put(keyChar, newWorMap);
                nowMap = newWorMap;
            }

            if (i == word.length() - 1) {
                // Song：最后一个
                nowMap.put("isEnd", "1");
            }
        }
    }

    private void addWordToMap(String word) {
        if (sensitiveWordMap == null) {
            sensitiveWordMap = new HashMap<>();
        }
        addWordToMap(word, sensitiveWordMap);
    }

    /**
     * Song：获取文字中的敏感词
     */
    private Set<String> getSensitiveWords(String text, int matchType) {
        Set<String> sensitiveWordList = new HashSet<>();
        if (sensitiveWordMap == null || sensitiveWordMap.isEmpty()) {
            return sensitiveWordList;
        }

        for (int i = 0; i < text.length(); i++) {
            int length = checkSensitiveWord(text, i, matchType);
            if (length > 0) {
                sensitiveWordList.add(text.substring(i, i + length));
                i = i + length - 1;
            }
        }
        return sensitiveWordList;
    }

    /**
     * Song：检查文字中是否包含敏感字符
     */
    private int checkSensitiveWord(String text, int beginIndex, int matchType) {
        boolean flag = false;
        int matchFlag = 0;
        char word;
        Map<Object, Object> nowMap = sensitiveWordMap;

        for (int i = beginIndex; i < text.length(); i++) {
            word = text.charAt(i);
            nowMap = (Map<Object, Object>) nowMap.get(word);
            if (nowMap != null) {
                // Song：存在，则判断是否为最后一个
                matchFlag++;
                if ("1".equals(nowMap.get("isEnd"))) {
                    // Song：如果为最后一个匹配规则,结束循环，返回匹配标识数
                    flag = true;
                    if (minMatchTYpe == matchType) {
                        break;
                    }
                }
            } else {
                // Song：不存在，直接返回
                break;
            }
        }
        if (matchType == maxMatchType && matchFlag < 2 && !flag) {
            // Song：长度必须大于等于1，为词
            matchFlag = 0;
        }
        if (!flag) {
            matchFlag = 0;
        }
        return matchFlag;
    }

    private String getReplaceChars(String charType, int length) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append(charType);
        }
        return result.toString();
    }
}
