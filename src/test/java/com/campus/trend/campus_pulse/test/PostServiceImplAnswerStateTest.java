package com.campus.trend.campus_pulse.test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.trend.campus_pulse.dto.request.PostSearchReq;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.AnswerAdoptionMapper;
import com.campus.trend.campus_pulse.mapper.PostVersionHistoryMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.AiPostAnalysisService;
import com.campus.trend.campus_pulse.service.AsyncTaskService;
import com.campus.trend.campus_pulse.service.BatchUserService;
import com.campus.trend.campus_pulse.service.ContentSecurityService;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.PollService;
import com.campus.trend.campus_pulse.service.PostCollectService;
import com.campus.trend.campus_pulse.service.PostEventService;
import com.campus.trend.campus_pulse.service.PostLikeService;
import com.campus.trend.campus_pulse.service.PostMediaService;
import com.campus.trend.campus_pulse.service.PostSubscriptionService;
import com.campus.trend.campus_pulse.service.SearchService;
import com.campus.trend.campus_pulse.service.SectionModeratorService;
import com.campus.trend.campus_pulse.service.SectionService;
import com.campus.trend.campus_pulse.service.SentimentAnalysisService;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.service.TrustLevelService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.service.UserTagRelationService;
import com.campus.trend.campus_pulse.service.impl.PostServiceImpl;
import com.campus.trend.campus_pulse.service.post.PostCacheManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceImplAnswerStateTest {

    @Mock private PostLikeService postLikeService;
    @Mock private PostCollectService postCollectService;
    @Mock private TagService tagService;
    @Mock private ContentSecurityService contentSecurityService;
    @Mock private SentimentAnalysisService sentimentAnalysisService;
    @Mock private UserService userService;
    @Mock private SectionService sectionService;
    @Mock private SectionModeratorService sectionModeratorService;
    @Mock private SectionMapper sectionMapper;
    @Mock private PostEventService postEventService;
    @Mock private NotificationService notificationService;
    @Mock private UserTagRelationService userTagRelationService;
    @Mock private com.campus.trend.campus_pulse.service.LevelService levelService;
    @Mock private AiPostAnalysisService aiPostAnalysisService;
    @Mock private ObjectMapper objectMapper;
    @Mock private PostVersionHistoryMapper postVersionHistoryMapper;
    @Mock private AsyncTaskService asyncTaskService;
    @Mock private PostMediaService postMediaService;
    @Mock private AnswerAdoptionMapper answerAdoptionMapper;
    @Mock private PollService pollService;
    @Mock private PostSubscriptionService postSubscriptionService;
    @Mock private PostCacheManager postCacheManager;
    @Mock private TrustLevelService trustLevelService;
    @Mock private SearchService searchService;
    @Mock private RBloomFilter<String> postIdBloomFilter;
    @Mock private BatchUserService batchUserService;

    @InjectMocks
    private PostServiceImpl postService;

    private Method buildSearchWrapperMethod;

    @BeforeEach
    void setUp() throws Exception {
        initLambdaCache();

        Class<?> keywordModeClass = Class.forName(PostServiceImpl.class.getName() + "$KeywordSearchMode");
        buildSearchWrapperMethod = PostServiceImpl.class.getDeclaredMethod(
                "buildSearchWrapper",
                PostSearchReq.class,
                List.class,
                keywordModeClass);
        buildSearchWrapperMethod.setAccessible(true);

        Section qaSection = new Section();
        qaSection.setId(11L);
        qaSection.setName("答疑解惑");
        qaSection.setStatus(1);
        lenient().when(sectionMapper.selectList(any())).thenReturn(List.of(qaSection));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void initLambdaCache() {
        if (TableInfoHelper.getTableInfo(Post.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), "test-post-mapper"),
                    Post.class);
        }
        if (TableInfoHelper.getTableInfo(Section.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), "test-section-mapper"),
                    Section.class);
        }
    }

    @Test
    void unsolvedNav_shouldForceQaSectionAndUnadoptedAnswers() throws Exception {
        PostSearchReq request = new PostSearchReq();
        request.setNavType("unsolved");
        request.setSectionId(3L);
        request.setCategory("3");
        request.setStatus(1);

        LambdaQueryWrapper<Post> wrapper = buildWrapper(request);

        String sql = normalizedSql(wrapper);
        assertThat(sql).contains("section_id in");
        assertThat(sql).contains("has_adopted_answer is null");
        assertThat(sql).contains("has_adopted_answer <>");
        assertThat(sql).doesNotContain("section_id =");
    }

    @Test
    void solvedAnswerState_shouldIgnoreConflictingSectionIds() throws Exception {
        PostSearchReq request = new PostSearchReq();
        request.setAnswerState("solved");
        request.setSectionIds(List.of(3L, 4L));
        request.setStatus(1);

        LambdaQueryWrapper<Post> wrapper = buildWrapper(request);

        String sql = normalizedSql(wrapper);
        assertThat(sql).contains("section_id in");
        assertThat(sql).contains("has_adopted_answer =");
        assertThat(sql).doesNotContain("section_id =");
    }

    @Test
    void publicSearch_shouldNotExposePendingAuditPosts() throws Exception {
        PostSearchReq request = new PostSearchReq();
        request.setStatus(1);
        request.setAuditStatus("PENDING");

        LambdaQueryWrapper<Post> wrapper = buildWrapper(request);

        String sql = normalizedSql(wrapper);
        assertThat(sql).contains("status =");
        assertThat(sql).contains("audit_status is null");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("PENDING", "APPROVED");
    }

    @Test
    void selfSearch_shouldAllowOwnPendingAuditPosts() throws Exception {
        authenticateAs("u-1");
        PostSearchReq request = new PostSearchReq();
        request.setUserId("u-1");
        request.setStatus(1);
        request.setAuditStatus("PENDING");

        LambdaQueryWrapper<Post> wrapper = buildWrapper(request);

        String sql = normalizedSql(wrapper);
        assertThat(sql).contains("user_id =", "audit_status =", "status =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("PENDING");
        assertThat(wrapper.getParamNameValuePairs().values()).doesNotContain("APPROVED");
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<Post> buildWrapper(PostSearchReq request) throws Exception {
        return (LambdaQueryWrapper<Post>) buildSearchWrapperMethod.invoke(
                postService,
                request,
                Collections.emptyList(),
                null);
    }

    private String normalizedSql(LambdaQueryWrapper<Post> wrapper) {
        return wrapper.getSqlSegment().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void authenticateAs(String userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("tester");
        user.setRole("ROLE_USER");
        AuthUser principal = new AuthUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
