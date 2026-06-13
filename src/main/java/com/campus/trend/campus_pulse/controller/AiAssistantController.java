package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.service.AiAssistantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI智能助手控制器
 */
@RestController
@RequestMapping("/ai-assistant")
@Slf4j
public class AiAssistantController {

    @Autowired
    private AiAssistantService aiAssistantService;

    /**
     * 查找相似已解决问题
     */
    @PostMapping("/similar-posts")
    public Result<List<Map<String, Object>>> findSimilarPosts(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        String content = request.get("content");
        List<Map<String, Object>> similarPosts = aiAssistantService.findSimilarPosts(title, content);
        return Result.success(similarPosts);
    }

    /**
     * 评估内容质量
     */
    @PostMapping("/evaluate-quality")
    public Result<Map<String, Object>> evaluateQuality(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        String content = request.get("content");
        Map<String, Object> evaluation = aiAssistantService.evaluateContentQuality(title, content);
        return Result.success(evaluation);
    }

    /**
     * 智能标签推荐
     */
    @PostMapping("/suggest-tags")
    public Result<List<String>> suggestTags(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        String content = request.get("content");
        List<String> tags = aiAssistantService.suggestTags(title, content);
        return Result.success(tags);
    }

    /**
     * 评论摘要生成
     */
    @GetMapping("/summarize-comments/{postId}")
    public Result<Map<String, String>> summarizeComments(@PathVariable String postId) {
        String summary = aiAssistantService.summarizeComments(postId);
        return Result.success(Map.of("summary", summary));
    }

    /**
     * 敏感内容检测
     */
    @PostMapping("/detect-sensitive")
    public Result<Map<String, Object>> detectSensitive(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        Map<String, Object> detection = aiAssistantService.detectSensitiveContent(content);
        return Result.success(detection);
    }
}
