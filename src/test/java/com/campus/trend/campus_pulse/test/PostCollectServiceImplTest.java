package com.campus.trend.campus_pulse.test;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostCollect;
import com.campus.trend.campus_pulse.mapper.PostCollectMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.impl.PostCollectServiceImpl;
import com.campus.trend.campus_pulse.service.post.PostCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCollectServiceImplTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostCollectMapper postCollectMapper;

    @Mock
    private LevelService levelService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PostCacheManager postCacheManager;

    private PostCollectServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PostCollectServiceImpl(postMapper, postCollectMapper, levelService, notificationService, postCacheManager);
        ReflectionTestUtils.setField(service, "baseMapper", postCollectMapper);
    }

    @Test
    void collectPost_shouldInsertRelationAndIncrementCountOnce() {
        Post post = new Post()
                .setId("POST_1")
                .setUserId("author_1")
                .setSectionId(10L)
                .setStatus(1)
                .setAuditStatus("APPROVED");
        when(postMapper.selectById("POST_1")).thenReturn(post);
        when(postCollectMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(postCollectMapper.insert(any(PostCollect.class))).thenReturn(1);

        boolean collected = service.collectPost("POST_1", "user_1");

        assertThat(collected).isTrue();
        verify(postMapper).incrementCollectCount("POST_1");
        verify(postCacheManager).invalidatePostCaches(10L, "POST_1");
        verify(levelService).addExperience("author_1", 3, "被收藏");
        verify(notificationService).sendFavoriteNotification("author_1", "user_1", "POST_1");
    }

    @Test
    void collectPost_shouldNotIncrementCount_whenRelationAlreadyExists() {
        Post post = new Post()
                .setId("POST_1")
                .setUserId("author_1")
                .setSectionId(10L)
                .setStatus(1)
                .setAuditStatus("APPROVED");
        when(postMapper.selectById("POST_1")).thenReturn(post);
        when(postCollectMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(postCollectMapper.insert(any(PostCollect.class))).thenThrow(new DuplicateKeyException("duplicate"));

        boolean collected = service.collectPost("POST_1", "user_1");

        assertThat(collected).isFalse();
        verify(postMapper, never()).incrementCollectCount(any());
        verify(postCacheManager, never()).invalidatePostCaches(any(), any());
        verify(levelService, never()).addExperience(any(), any(Integer.class), any());
        verify(notificationService, never()).sendFavoriteNotification(any(), any(), any());
    }

    @Test
    void uncollectPost_shouldRemoveRelationAndDecrementCount() {
        Post post = new Post()
                .setId("POST_1")
                .setUserId("author_1")
                .setSectionId(10L)
                .setStatus(1)
                .setAuditStatus("APPROVED");
        when(postMapper.selectById("POST_1")).thenReturn(post);
        when(postCollectMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(postCollectMapper.delete(any(Wrapper.class))).thenReturn(1);

        boolean uncollected = service.uncollectPost("POST_1", "user_1");

        assertThat(uncollected).isTrue();
        verify(postMapper).decrementCollectCount("POST_1");
        verify(postCacheManager).invalidatePostCaches(eq(10L), eq("POST_1"));
    }
}
