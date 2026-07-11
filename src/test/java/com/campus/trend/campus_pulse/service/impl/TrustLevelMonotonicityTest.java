package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.config.properties.TrustLevelProperties;
import com.campus.trend.campus_pulse.dto.response.TrustMetricsSnapshot;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.TrustEventMapper;
import com.campus.trend.campus_pulse.mapper.TrustMetricsMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustLevelMonotonicityTest {

    @Mock private UserMapper userMapper;
    @Mock private TrustMetricsMapper trustMetricsMapper;
    @Mock private TrustEventMapper trustEventMapper;
    @Mock private NotificationService notificationService;

    private TrustLevelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TrustLevelServiceImpl(
                userMapper,
                trustMetricsMapper,
                trustEventMapper,
                new TrustLevelProperties(),
                new ObjectMapper(),
                notificationService);
    }

    @Test
    void recalculate_shouldNotDowngradeTl2WhenLifetimeAchievementIsAlreadyEarned() {
        User user = regularUser("user-1", 2);
        when(userMapper.selectById(user.getId())).thenReturn(user);
        when(trustMetricsMapper.selectSnapshot(eq(user.getId()), any(LocalDateTime.class)))
                .thenReturn(new TrustMetricsSnapshot());

        assertThat(service.recalculateAndPromote(user.getId())).isFalse();

        verify(userMapper, never()).updateTrustLevelIfCurrent(any(), anyInt(), anyInt());
        verifyNoInteractions(trustEventMapper, notificationService);
    }

    @Test
    void recalculate_shouldDowngradeInactiveTl3ToTl2UsingCompareAndSwap() {
        User user = regularUser("user-2", 3);
        when(userMapper.selectById(user.getId())).thenReturn(user);
        when(trustMetricsMapper.selectSnapshot(eq(user.getId()), any(LocalDateTime.class)))
                .thenReturn(new TrustMetricsSnapshot());
        when(userMapper.updateTrustLevelIfCurrent(user.getId(), 3, 2)).thenReturn(1);

        assertThat(service.recalculateAndPromote(user.getId())).isTrue();

        verify(userMapper).updateTrustLevelIfCurrent(user.getId(), 3, 2);
    }

    @Test
    void recalculate_shouldNeverAutomaticallyRewriteAdministratorTrustLevel() {
        User admin = regularUser("admin-1", 0).setRole("ROLE_ADMIN");
        when(userMapper.selectById(admin.getId())).thenReturn(admin);

        assertThat(service.recalculateAndPromote(admin.getId())).isFalse();

        verifyNoInteractions(trustMetricsMapper, trustEventMapper, notificationService);
        verify(userMapper, never()).updateTrustLevelIfCurrent(any(), anyInt(), anyInt());
    }

    private User regularUser(String id, int trustLevel) {
        return new User()
                .setId(id)
                .setRole("ROLE_USER")
                .setTrustLevel(trustLevel)
                .setCreateTime(LocalDateTime.now().minusDays(30));
    }
}
