package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.dto.request.GrowthEventReq;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrowthAnalyticsServiceImplTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void recordsOnlyAllowlistedPropertiesAndRemovesQueryFromRoute() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        GrowthAnalyticsServiceImpl service = new GrowthAnalyticsServiceImpl(jdbc, new ObjectMapper());
        GrowthEventReq request = new GrowthEventReq();
        request.setEventName("POST_READ");
        request.setAnonymousId("anon_12345678");
        request.setSessionId("session_12345678");
        request.setRoute("/t/42?token=must-not-be-stored");
        request.setSource("Search.Example");
        request.setProperties(Map.of("postId", "42", "content", "private body"));

        service.record(request, "user-1");

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), args.capture());
        Object[] values = args.getValue();
        assertEquals("post_read", values[0]);
        assertEquals("/t/42", values[4]);
        assertEquals("search.example", values[5]);
        assertTrue(String.valueOf(values[6]).contains("postId"));
        assertFalse(String.valueOf(values[6]).contains("private body"));
    }

    @Test
    void rejectsUnknownEventBeforeDatabaseWrite() {
        GrowthAnalyticsServiceImpl service = new GrowthAnalyticsServiceImpl(jdbc, new ObjectMapper());
        GrowthEventReq request = new GrowthEventReq();
        request.setEventName("password_captured");
        request.setAnonymousId("anon_12345678");
        request.setSessionId("session_12345678");
        assertThrows(IllegalArgumentException.class, () -> service.record(request, null));
        verifyNoInteractions(jdbc);
    }
}
