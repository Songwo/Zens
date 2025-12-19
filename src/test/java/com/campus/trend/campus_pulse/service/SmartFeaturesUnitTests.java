package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.entity.SysViewLog;
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import com.campus.trend.campus_pulse.mapper.SysViewLogMapper;
import com.campus.trend.campus_pulse.service.impl.CollaborativeFilteringServiceImpl;
import com.campus.trend.campus_pulse.service.impl.ContentSecurityServiceImpl;
import com.campus.trend.campus_pulse.service.impl.SentimentAnalysisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 智能功能综合测试
 * 不依赖Spring容器，运行速度快
 */
@ExtendWith(MockitoExtension.class)
public class SmartFeaturesUnitTests {

    private ContentSecurityServiceImpl contentSecurityService;
    private SentimentAnalysisServiceImpl sentimentAnalysisService;
    private CollaborativeFilteringServiceImpl collaborativeFilteringService;

    @Mock
    private SysViewLogMapper viewLogMapper;
    @Mock
    private SysPostMapper postMapper;

    @BeforeEach
    void setUp() {
        // 1. 初始化内容安全服务
        contentSecurityService = new ContentSecurityServiceImpl();
        // 手动注入各种可能的依赖，或者让其加载真实文件
        // 由于测试环境也是基于类路径，这里直接调用init加载真实字典
        contentSecurityService.init();

        // 2. 初始化情感分析服务
        sentimentAnalysisService = new SentimentAnalysisServiceImpl();
        sentimentAnalysisService.init();

        // 3. 初始化协同过滤服务
        collaborativeFilteringService = new CollaborativeFilteringServiceImpl(viewLogMapper, postMapper);
    }

    // ==========================================
    // 1. 内容安全测试 (DFA算法)
    // ==========================================
    @Test
    @DisplayName("测试敏感词检测")
    void testSensitiveWordDetection() {
        // 假设 sensitive_words.txt 中包含 "作弊", "笨蛋"

        String cleanText = "这是一个正常的校园帖子，大家来图书馆学习吧。";
        assertFalse(contentSecurityService.containsSensitiveWords(cleanText), "正常文本不应检测出敏感词");

        String dirtyText = "有人需要代写作业吗？或者是作弊的方法？";
        assertTrue(contentSecurityService.containsSensitiveWords(dirtyText), "包含'作弊'的文本应被检测出");

        String dirtyText2 = "你个大笨蛋";
        Set<String> words = contentSecurityService.getSensitiveWords(dirtyText2);
        assertTrue(words.contains("笨蛋"), "应该能提取出'笨蛋'这个词");
    }

    @Test
    @DisplayName("测试敏感词过滤")
    void testSensitiveWordFiltering() {
        String input = "禁止作弊，不要当笨蛋";
        String output = contentSecurityService.filterSensitiveWords(input);

        // 期望：禁止**，不要当** (根据长度替换)
        // 作弊 -> ** (2个字)
        // 笨蛋 -> ** (2个字)

        assertFalse(output.contains("作弊"));
        assertFalse(output.contains("笨蛋"));
        assertTrue(output.contains("**"));

        System.out.println("Filter Result: " + output);
    }

    // ==========================================
    // 2. 情感分析测试 (字典匹配)
    // ==========================================
    @Test
    @DisplayName("测试情感分析得分")
    void testSentimentAnalysis() {
        // 正面测试
        String positiveText = "今天真开心，考研成功上岸了，非常感谢大家的帮助！";
        double posScore = sentimentAnalysisService.analyzeSentiment(positiveText);
        System.out.println("Positive Score: " + posScore);
        assertTrue(posScore > 0.5, "正面文本得分应大于0.5");
        assertEquals("POSITIVE", sentimentAnalysisService.getSentimentLabel(posScore));

        // 负面测试
        String negativeText = "太难过了，考试不及格，非常失望，感觉很糟糕。";
        double negScore = sentimentAnalysisService.analyzeSentiment(negativeText);
        System.out.println("Negative Score: " + negScore);
        assertTrue(negScore < 0.5, "负面文本得分应小于0.5");
        assertEquals("NEGATIVE", sentimentAnalysisService.getSentimentLabel(negScore));

        // 中性测试
        String neutralText = "今天天气一般，我去食堂吃了个饭。";
        double neutralScore = sentimentAnalysisService.analyzeSentiment(neutralText);
        System.out.println("Neutral Score: " + neutralScore);
        // 简单字典法如果不包含关键词，默认返回0.5
        assertEquals(0.5, neutralScore, 0.1);
    }

    // ==========================================
    // 3. 协同过滤测试 (Item-Based CF)
    // ==========================================
    @Test
    @DisplayName("测试协同过滤推荐")
    @org.junit.jupiter.api.Disabled("需要完整的 Mybatis-Plus 环境支持，暂跳过")
    void testCollaborativeFiltering() {
        String currentPostId = "p_java_basics";

        // Step 1: Mock 谁看了 currentPostId
        // 假设 UserA, UserB 看了 p_java_basics
        List<SysViewLog> whoViewed = Arrays.asList(
                new SysViewLog().setUserId("UserA"),
                new SysViewLog().setUserId("UserB"));

        // 当调用 selectList 且查询条件匹配 currentPostId 时返回 whoViewed
        // 由于 MyBatis-Plus 的 LambdaQueryWrapper 比较难 mock eq 匹配，
        // 这里简化为：第一次调用 selectList 返回 whoViewed
        when(viewLogMapper.selectList(any())).thenReturn(whoViewed) // result for step 1
                .thenReturn(buildOtherLogs()); // result for step 2

        // Step 2: Mock 这些用户看了什么其他帖子
        // UserA 看了: p_spring_boot, p_mybatis
        // UserB 看了: p_spring_boot, p_ai_intro
        // 统计: p_spring_boot(2次), p_mybatis(1次), p_ai_intro(1次)
        // 预期推荐: p_spring_boot 排第一

        // Step 3: Mock selectBatchIds 返回帖子详情
        when(postMapper.selectBatchIds(any())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            List<SysPost> posts = new ArrayList<>();
            for (String id : ids) {
                posts.add(new SysPost().setId(id).setTitle("Title for " + id));
            }
            return posts;
        });

        // 执行测试
        List<SysPost> recommendations = collaborativeFilteringService.recommendByItemBased(currentPostId, 5);

        // 验证
        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty(), "推荐列表不应为空");

        // 验证顺序：p_spring_boot 应该在最前面
        // 注意：由于我们在 when(viewLogMapper...) 中使用了两次 thenReturn，
        // 必须确保 service 实际上进行了两次 selectList 调用。
        // 实现逻辑是：
        // 1. selectList (查userIds)
        // 2. selectList (查otherViewLogs)

        System.out.println("Recommendations: " + recommendations.size());
        for (SysPost p : recommendations) {
            System.out.println("Rec: " + p.getId());
        }

        assertEquals("p_spring_boot", recommendations.get(0).getId());
    }

    private List<SysViewLog> buildOtherLogs() {
        return Arrays.asList(
                new SysViewLog().setUserId("UserA").setPostId("p_spring_boot"),
                new SysViewLog().setUserId("UserA").setPostId("p_mybatis"),
                new SysViewLog().setUserId("UserB").setPostId("p_spring_boot"),
                new SysViewLog().setUserId("UserB").setPostId("p_ai_intro"));
    }
}
