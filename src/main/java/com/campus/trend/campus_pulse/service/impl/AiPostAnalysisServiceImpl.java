package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.dto.request.TagsExtractReq;
import com.campus.trend.campus_pulse.service.AiPostAnalysisService;
import com.campus.trend.campus_pulse.service.DeepSeekService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiPostAnalysisServiceImpl implements AiPostAnalysisService {

    private static final int MAX_CONTENT_LENGTH = 12000;
    private static final int MIN_TAG_SIZE = 1;
    private static final int MAX_TAG_SIZE = 10;
    private static final int MIN_SUMMARY_SIZE = 50;
    private static final int MAX_SUMMARY_SIZE = 300;

    private final DeepSeekService deepSeekService;

    @Override
    public Map<String, Object> extractTagsAndSummary(TagsExtractReq request) {
        String purifiedText = sanitizeContent(request.getContent());
        int tagSize = clamp(request.getTagSize(), MIN_TAG_SIZE, MAX_TAG_SIZE);
        int summarySize = clamp(request.getSummarySize(), MIN_SUMMARY_SIZE, MAX_SUMMARY_SIZE);

        log.info("执行 AI 内容分析: textLength={}, tagSize={}, summarySize={}",
                purifiedText.length(), tagSize, summarySize);

        return deepSeekService.extractTagsAndSummary(purifiedText, tagSize, summarySize);
    }

    private String sanitizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }

        String plainText = content
                .replaceAll("<[^>]*>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (plainText.length() > MAX_CONTENT_LENGTH) {
            log.warn("AI 内容过长，已截断: originLength={}, maxLength={}",
                    plainText.length(), MAX_CONTENT_LENGTH);
            return plainText.substring(0, MAX_CONTENT_LENGTH);
        }

        return plainText;
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
