package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.controller.InternalAuthController;
import com.campus.trend.campus_pulse.dto.request.LoginReq;
import com.campus.trend.campus_pulse.dto.response.LoginResponse;
import com.campus.trend.campus_pulse.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private InternalAuthController controller;

    @Test
    void lotteryServiceGetsNormalPasswordSession() {
        InternalAuthController.BotLoginRequest request = new InternalAuthController.BotLoginRequest();
        request.setAccount("zens-lottery-bot");
        request.setPassword("strong-password");
        LoginResponse response = new LoginResponse();
        response.setAccessToken("issued-token");
        when(authService.login(any(LoginReq.class), anyString(), anyString())).thenReturn(response);

        Result<LoginResponse> result = controller.botLogin(request, "campus-lottery-station");

        assertSame(response, result.getData());
        ArgumentCaptor<LoginReq> loginCaptor = ArgumentCaptor.forClass(LoginReq.class);
        verify(authService).login(
                loginCaptor.capture(),
                eq("zens-lottery-station"),
                eq("internal-service:campus-lottery-station"));
        LoginReq login = loginCaptor.getValue();
        assertEquals("password", login.getLoginType());
        assertEquals("zens-lottery-bot", login.getAccount());
        assertEquals("strong-password", login.getPassword());
        assertTrue(login.isRememberMe());
    }

    @Test
    void otherInternalServicesCannotRequestBotSession() {
        InternalAuthController.BotLoginRequest request = new InternalAuthController.BotLoginRequest();
        request.setAccount("zens-lottery-bot");
        request.setPassword("strong-password");

        Result<LoginResponse> result = controller.botLogin(request, "zdc-shop");

        assertEquals(3003, result.getCode());
        verify(authService, never()).login(any(LoginReq.class), anyString(), anyString());
    }
}
