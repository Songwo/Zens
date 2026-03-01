package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.service.DeepSeekService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class DeepSeekServiceImpl implements DeepSeekService {

    @Value("${ai.deepseek.api-url:https://api.deepseek.com/chat/completions}")
    private String apiUrl;

    @Value("${ai.deepseek.api-key:your-api-key-here}")
    private String apiKey;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DeepSeekServiceImpl() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Map<String, Object> extractTagsAndSummary(String content, int tagSize, int summarySize) {
        Map<String, Object> result = new HashMap<>();
        List<String> defaultTags = new ArrayList<>();
        String defaultSummary = content.length() > summarySize ? content.substring(0, summarySize) + "..." : content;

        // Song：兜底默认值
        result.put("tags", defaultTags);
        result.put("summary", defaultSummary);

        if (!StringUtils.hasText(content) || "your-api-key-here".equals(apiKey)) {
            log.warn("DeepSeek API Key 未配置或内容为空，走默认逻辑");
            return result;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            String systemPrompt = "你是一个专业的内容分析助手。请从用户提供的内容中提取关键字标签和内容摘要。返回纯JSON格式，禁止输出任何其他解释文字。返回格式：{\"tags\": [\"标签1\", \"标签2\"], \"summary\": \"生成的摘要文本\"}";
            String userPrompt = String.format("提取 3 到 %d 个核心关键字标签，摘要长度控制在 %d 字以内。内容如下：\n%s", tagSize, summarySize,
                    content);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("response_format", Map.of("type", "json_object")); // Song：说明

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.3); // Song：低温度保证输出稳定

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String requestUrl = apiUrl;
            if (!requestUrl.endsWith("/chat/completions")) {
                if (requestUrl.endsWith("/")) {
                    requestUrl += "chat/completions";
                } else {
                    requestUrl += "/chat/completions";
                }
            }

            ResponseEntity<String> response = restTemplate.postForEntity(requestUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                JsonNode messageNode = rootNode.path("choices").path(0).path("message").path("content");

                if (!messageNode.isMissingNode()) {
                    String jsonString = messageNode.asText();

                    // Song：说明
                    jsonString = jsonString.replaceAll("```json", "")
                            .replaceAll("```", "")
                            .trim();

                    // Song：说明
                    JsonNode resultNode = objectMapper.readTree(jsonString);

                    if (resultNode.has("tags") && resultNode.get("tags").isArray()
                            && resultNode.get("tags").size() > 0) {
                        List<String> tags = new ArrayList<>();
                        resultNode.get("tags").forEach(tag -> tags.add(tag.asText()));
                        result.put("tags", tags);
                    }
                    if (resultNode.has("summary") && StringUtils.hasText(resultNode.get("summary").asText())) {
                        result.put("summary", resultNode.get("summary").asText());
                    }
                    log.info("DeepSeek AI 提取成功: {}", result);
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败，退化为默认方案", e);
        }

        return result;
    }
}
