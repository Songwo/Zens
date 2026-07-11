package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostLike;
import com.campus.trend.campus_pulse.mapper.PostLikeMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostLikeBehaviorCounterTest {

    @Mock private PostMapper postMapper;
    @Mock private PostLikeMapper postLikeMapper;
    @Mock private UserService userService;
    @Mock private LevelService levelService;
    @Mock private NotificationService notificationService;

    private PostLikeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PostLikeServiceImpl(postMapper, userService, levelService, notificationService);
        ReflectionTestUtils.setField(service, "baseMapper", postLikeMapper);
    }

    @Test
    void likeAndUnlike_shouldMaintainGivenLikeCounterSymmetrically() {
        Post post = new Post().setId("post-1").setUserId("author-1").setStatus(1).setAuditStatus("APPROVED");
        when(postMapper.selectById("post-1")).thenReturn(post);
        when(postLikeMapper.selectCount(any(Wrapper.class))).thenReturn(0L, 1L);
        when(postLikeMapper.insert(any(PostLike.class))).thenReturn(1);
        when(postLikeMapper.delete(any(Wrapper.class))).thenReturn(1);

        service.likePost("post-1", "reader-1");
        service.unlikePost("post-1", "reader-1");

        verify(userService).incrementLikesGiven("reader-1");
        verify(userService).decrementLikesGiven("reader-1");
    }
}
