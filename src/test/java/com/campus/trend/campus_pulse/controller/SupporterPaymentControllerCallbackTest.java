package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.service.SupporterPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SupporterPaymentControllerCallbackTest {

    @Test
    void callback_shouldAckSuccessAsPlainTextAfterTerminalProcessing() {
        SupporterPaymentController controller = new SupporterPaymentController(mock(SupporterPaymentService.class));

        ResponseEntity<String> response = controller.callback("alipay", "payload", Map.of());

        assertThat(response.getBody()).isEqualTo("success");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
    }

    @Test
    void callback_shouldReturnPlainTextFailureForRetryableServiceFailure() {
        SupporterPaymentService service = mock(SupporterPaymentService.class);
        doThrow(new IllegalStateException("transient")).when(service)
                .handleCallback("alipay", "payload", Map.of());
        SupporterPaymentController controller = new SupporterPaymentController(service);

        ResponseEntity<String> response = controller.callback("alipay", "payload", Map.of());

        assertThat(response.getBody()).isEqualTo("failure");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
    }
}
