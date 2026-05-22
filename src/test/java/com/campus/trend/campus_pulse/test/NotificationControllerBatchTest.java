package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.controller.NotificationController;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerBatchTest {

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController(notificationService);
        mockMvc = ControllerTestSupport.standaloneWithValidation(controller);
        objectMapper = new ObjectMapper();
        ControllerTestSupport.mockLoginUser("u100");
    }

    @AfterEach
    void tearDown() {
        ControllerTestSupport.clearSecurity();
    }

    @Test
    void markBatchAsRead_shouldCallService_whenIdsValid() throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of("ids", List.of(1, 2, 2, 3)));

        mockMvc.perform(put("/notification/read-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000));

        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationService, times(1)).markBatchAsRead(idsCaptor.capture(), eq("u100"));
        assertThat(idsCaptor.getValue()).containsExactly(1L, 2L, 2L, 3L);
    }

    @Test
    void markBatchAsRead_shouldReturnValidateError_whenIdsEmpty() throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of("ids", List.of()));

        mockMvc.perform(put("/notification/read-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4003))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ids")));

        verify(notificationService, never()).markBatchAsRead(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void deleteBatch_shouldCallService_whenIdsValid() throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of("ids", List.of(8, 9)));

        mockMvc.perform(delete("/notification/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000));

        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationService, times(1)).deleteBatch(idsCaptor.capture(), eq("u100"));
        assertThat(idsCaptor.getValue()).containsExactly(8L, 9L);
    }
}
