package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.service.SupporterFeedbackService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupporterFeedbackControllerTest {
    @Test
    void mine_shouldReturnHttp403ForUserWithoutPlusEntitlement() {
        SupporterFeedbackService service = mock(SupporterFeedbackService.class);
        when(service.pageMine(any(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(ResultCode.NO_PERMISSION, "仅共建支持者可用"));
        SupporterFeedbackController controller = new SupporterFeedbackController(service);

        var response = controller.mine(1, 10);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ResultCode.NO_PERMISSION.getCode());
    }
}
