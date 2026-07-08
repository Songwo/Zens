package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.AiAssistantService;
import com.campus.trend.campus_pulse.service.AiPostAnalysisService;
import com.campus.trend.campus_pulse.service.ContentSecurityService;
import com.campus.trend.campus_pulse.service.DeepSeekService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI智能助手服务实现
 */
@Slf4j
@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private AiPostAnalysisService aiPostAnalysisService;

    @Autowired
    private DeepSeekService deepSeekService;

    @Autowired
    private ContentSecurityService contentSecurityService;

    @Override
    public List<Map<String, Object>> findSimilarPosts(String title, String content) {
        try {
            // 1. 查询相似帖子（有采纳答案的优先）
            LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Post::getStatus, 1)
                    .and(w -> w.isNull(Post::getAuditStatus)
                            .or()
                            .eq(Post::getAuditStatus, "")
                            .or()
                            .eq(Post::getAuditStatus, "APPROVED"))
                    .orderByDesc(Post::getHeatScore)
                    .last("LIMIT 10");

            List<Post> posts = postMapper.selectList(wrapper);

            // 2. 计算相似度并返回
            return posts.stream()
                    .map(post -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("postId", post.getId());
                        map.put("title", post.getTitle());
                        map.put("summary", post.getSummary());
                        map.put("hasAdoptedAnswer", post.getHasAdoptedAnswer() != null ? post.getHasAdoptedAnswer() : 0);
                        map.put("likeCount", post.getLikeCount());
                        map.put("commentCount", post.getCommentCount());

                        // 简单的相似度计算
                        double similarity = calculateSimilarity(title + content, post.getTitle() + post.getContent());
                        map.put("similarity", similarity);

                        return map;
                    })
                    .sorted((a, b) -> Double.compare((Double) b.get("similarity"), (Double) a.get("similarity")))
                    .limit(5)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("查找相似帖子失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public Map<String, Object> evaluateContentQuality(String title, String content) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 基础检查
            int titleLength = title != null ? title.length() : 0;
            int contentLength = content != null ? content.length() : 0;

            int score = 50; // 基础分
            List<String> suggestions = new ArrayList<>();

            // 2. 标题质量
            if (titleLength < 10) {
                suggestions.add("标题过短，建议至少10个字");
                score -= 10;
            } else if (titleLength > 100) {
                suggestions.add("标题过长，建议不超过100个字");
                score -= 5;
            } else {
                score += 10;
            }

            // 3. 内容质量
            if (contentLength < 50) {
                suggestions.add("内容过于简单，建议详细描述问题");
                score -= 15;
            } else if (contentLength < 200) {
                suggestions.add("内容可以更详细一些");
                score -= 5;
            } else if (contentLength > 200 && contentLength < 1000) {
                score += 15;
            } else if (contentLength >= 1000) {
                score += 20;
            }

            // 4. 格式检查
            if (content != null) {
                if (content.contains("```")) {
                    suggestions.add("包含代码块，格式良好");
                    score += 10;
                }
                if (content.contains("\n\n")) {
                    suggestions.add("段落分明，可读性好");
                    score += 5;
                }
                // 检查是否有列表
                if (content.contains("- ") || content.contains("1. ")) {
                    suggestions.add("使用了列表格式");
                    score += 5;
                }
            }

            // 5. 敏感词检查
            boolean hasSensitive = contentSecurityService.containsSensitiveWords(title + " " + content);
            if (hasSensitive) {
                suggestions.add("⚠️ 内容可能包含敏感词，请修改");
                score -= 30;
            }

            // 确保分数在0-100之间
            score = Math.max(0, Math.min(100, score));

            result.put("score", score);
            result.put("level", score >= 80 ? "优秀" : score >= 60 ? "良好" : score >= 40 ? "及格" : "待改进");
            result.put("suggestions", suggestions);

        } catch (Exception e) {
            log.error("评估内容质量失败", e);
            result.put("score", 50);
            result.put("level", "未知");
            result.put("suggestions", List.of("评估失败，请稍后再试"));
        }

        return result;
    }

    @Override
    public List<String> suggestTags(String title, String content) {
        try {
            // 简单的关键词提取（基于词频）
            String text = title + " " + content;
            String[] words = text.split("[\\s,，。！？、;；:：]+");

            Map<String, Integer> wordCount = new HashMap<>();
            for (String word : words) {
                word = word.trim();
                if (word.length() >= 2 && word.length() <= 10) {
                    wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
                }
            }

            // 按频率排序，取前5个
            return wordCount.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("生成标签建议失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public String summarizeComments(String postId) {
        try {
            // 1. 获取评论
            LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Comment::getPostId, postId)
                    .eq(Comment::getAuditStatus, "APPROVED")
                    .orderByDesc(Comment::getLikeCount)
                    .last("LIMIT 20");

            List<Comment> comments = commentMapper.selectList(wrapper);

            if (comments.isEmpty()) {
                return "暂无评论";
            }

            // 2. 拼接评论内容
            String combinedComments = comments.stream()
                    .map(Comment::getContent)
                    .collect(Collectors.joining("\n"));

            // 3. 简单摘要：返回高赞评论
            if (comments.size() > 3) {
                return String.format("共 %d 条评论，高赞观点：%s",
                    comments.size(),
                    comments.get(0).getContent().substring(0, Math.min(100, comments.get(0).getContent().length())));
            } else {
                return "高赞评论：" + (comments.isEmpty() ? "无" : comments.get(0).getContent());
            }

        } catch (Exception e) {
            log.error("生成评论摘要失败", e);
            return "摘要生成失败";
        }
    }

    @Override
    public Map<String, Object> detectSensitiveContent(String content) {
        Map<String, Object> result = new HashMap<>();

        try {
            boolean hasSensitive = contentSecurityService.containsSensitiveWords(content);
            result.put("hasSensitive", hasSensitive);
            result.put("isSafe", !hasSensitive);

            if (hasSensitive) {
                result.put("suggestion", "内容包含敏感词，建议修改后发布");
                result.put("action", "REJECT");
            } else {
                result.put("suggestion", "内容安全，可以发布");
                result.put("action", "APPROVE");
            }

        } catch (Exception e) {
            log.error("检测敏感内容失败", e);
            result.put("hasSensitive", false);
            result.put("isSafe", true);
            result.put("suggestion", "检测失败");
            result.put("action", "UNKNOWN");
        }

        return result;
    }

    /**
     * 简单的相似度计算（余弦相似度的简化版本）
     */
    private double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }

        Set<String> words1 = new HashSet<>(Arrays.asList(text1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.split("\\s+")));

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }
}
