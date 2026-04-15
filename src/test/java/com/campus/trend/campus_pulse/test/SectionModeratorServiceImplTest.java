package com.campus.trend.campus_pulse.test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.entity.ModeratorApplication;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.SysReport;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.mapper.ModeratorApplicationMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.impl.SectionModeratorServiceImpl;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionModeratorServiceImplTest {

    @Mock
    private ModeratorApplicationMapper moderatorApplicationMapper;

    @Mock
    private PostMapper postMapper;

    @Mock
    private CommentMapper commentMapper;

    private void initLambdaCache() {
        if (TableInfoHelper.getTableInfo(ModeratorApplication.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), "test-mapper"),
                    ModeratorApplication.class);
        }
    }

    @Test
    void getModeratedSectionIds_shouldReturnApprovedSections() {
        initLambdaCache();
        SectionModeratorServiceImpl service = new SectionModeratorServiceImpl(
                moderatorApplicationMapper, postMapper, commentMapper);

        ModeratorApplication app1 = new ModeratorApplication();
        app1.setSectionId(3L);
        ModeratorApplication app2 = new ModeratorApplication();
        app2.setSectionId(8L);
        when(moderatorApplicationMapper.selectList(any())).thenReturn(List.of(app1, app2));

        try (MockedStatic<PermissionUtils> permissionUtils = mockStatic(PermissionUtils.class)) {
            permissionUtils.when(() -> PermissionUtils.isUserAdmin("mod-1")).thenReturn(false);

            Set<Long> sectionIds = service.getModeratedSectionIds("mod-1");

            assertEquals(Set.of(3L, 8L), sectionIds);
            assertTrue(service.hasModeratorCapability("mod-1"));
            assertTrue(service.isSectionModerator("mod-1", 3L));
            assertFalse(service.isSectionModerator("mod-1", 9L));
        }
    }

    @Test
    void canModerateReport_shouldRespectSectionScopeForPostAndComment() {
        initLambdaCache();
        SectionModeratorServiceImpl service = new SectionModeratorServiceImpl(
                moderatorApplicationMapper, postMapper, commentMapper);

        ModeratorApplication approved = new ModeratorApplication();
        approved.setSectionId(7L);
        when(moderatorApplicationMapper.selectList(any())).thenReturn(List.of(approved));

        Post allowedPost = new Post();
        allowedPost.setId("post-1");
        allowedPost.setSectionId(7L);
        when(postMapper.selectById("post-1")).thenReturn(allowedPost);

        Post forbiddenPost = new Post();
        forbiddenPost.setId("post-2");
        forbiddenPost.setSectionId(9L);
        when(postMapper.selectById("post-2")).thenReturn(forbiddenPost);

        Comment allowedComment = new Comment();
        allowedComment.setId("comment-1");
        allowedComment.setPostId("post-1");
        when(commentMapper.selectById("comment-1")).thenReturn(allowedComment);

        Comment forbiddenComment = new Comment();
        forbiddenComment.setId("comment-2");
        forbiddenComment.setPostId("post-2");
        when(commentMapper.selectById("comment-2")).thenReturn(forbiddenComment);

        SysReport postReport = new SysReport();
        postReport.setTargetType("post");
        postReport.setTargetId("post-1");

        SysReport commentReport = new SysReport();
        commentReport.setTargetType("comment");
        commentReport.setTargetId("comment-1");

        SysReport forbiddenReport = new SysReport();
        forbiddenReport.setTargetType("comment");
        forbiddenReport.setTargetId("comment-2");

        try (MockedStatic<PermissionUtils> permissionUtils = mockStatic(PermissionUtils.class)) {
            permissionUtils.when(() -> PermissionUtils.isUserAdmin("mod-1")).thenReturn(false);

            assertTrue(service.canModeratePost("mod-1", "post-1"));
            assertTrue(service.canModerateComment("mod-1", "comment-1"));
            assertTrue(service.canModerateReport("mod-1", postReport));
            assertTrue(service.canModerateReport("mod-1", commentReport));

            assertFalse(service.canModeratePost("mod-1", "post-2"));
            assertFalse(service.canModerateComment("mod-1", "comment-2"));
            assertFalse(service.canModerateReport("mod-1", forbiddenReport));
        }
    }

    @Test
    void resolveReportSectionId_shouldResolveCommentToPostSection() {
        initLambdaCache();
        SectionModeratorServiceImpl service = new SectionModeratorServiceImpl(
                moderatorApplicationMapper, postMapper, commentMapper);

        Comment comment = new Comment();
        comment.setId("comment-9");
        comment.setPostId("post-9");
        when(commentMapper.selectById("comment-9")).thenReturn(comment);

        Post post = new Post();
        post.setId("post-9");
        post.setSectionId(12L);
        when(postMapper.selectById("post-9")).thenReturn(post);

        SysReport report = new SysReport();
        report.setTargetType("comment");
        report.setTargetId("comment-9");

        assertEquals(12L, service.resolveReportSectionId(report));
    }
}
