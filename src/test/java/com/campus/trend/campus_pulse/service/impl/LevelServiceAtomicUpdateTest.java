package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.entity.LevelExpLog;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.LevelExpLogMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LevelServiceAtomicUpdateTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private LevelExpLogMapper levelExpLogMapper;

    @Test
    void addExperience_shouldUseAtomicFieldUpdateAndSynchronizeStoredLevel() {
        LevelServiceImpl service = new LevelServiceImpl(userMapper, levelExpLogMapper);
        User fresh = new User().setId("user-1").setExperience(301).setLevel(1);
        when(userMapper.addExperienceAtomic("user-1", 2)).thenReturn(1);
        when(userMapper.selectById("user-1")).thenReturn(fresh);
        when(levelExpLogMapper.insert(any(LevelExpLog.class))).thenReturn(1);

        service.addExperience("user-1", 2, "test");

        verify(userMapper).addExperienceAtomic("user-1", 2);
        verify(userMapper).raiseLevelAtLeast("user-1", 3);
        ArgumentCaptor<LevelExpLog> logCaptor = ArgumentCaptor.forClass(LevelExpLog.class);
        verify(levelExpLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getExpDelta()).isEqualTo(2);
        assertThat(logCaptor.getValue().getReason()).isEqualTo("test");
    }
}
