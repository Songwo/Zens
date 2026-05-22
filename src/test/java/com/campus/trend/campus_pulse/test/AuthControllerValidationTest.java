package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.config.TurnstileProperties;
import com.campus.trend.campus_pulse.controller.AuthController;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.TurnstileService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.service.VerificationCodeService;
import com.campus.trend.campus_pulse.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerValidationTest {

    @Mock
    private AuthService authService;
    @Mock
    private TurnstileService turnstileService;
    @Mock
    private VerificationCodeService verificationCodeService;
    @Mock
    private UserService userService;
    @Mock
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TurnstileProperties turnstileProperties = new TurnstileProperties();
        turnstileProperties.setAllowLocalPreviewSkip(false);
        AuthController authController = new AuthController(
                authService,
                turnstileService,
                turnstileProperties,
                verificationCodeService,
                userService,
                jwtUtil);
        mockMvc = ControllerTestSupport.standaloneWithValidation(authController);
        objectMapper = new ObjectMapper();
    }

    @Test
    void refresh_shouldReturnValidateError_whenRefreshTokenBlank() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("refreshToken", " "));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4003))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("refreshToken")));

        verify(authService, never()).refresh(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void register_shouldReturnValidateError_whenEmailInvalid() throws Exception {
        String payload = """
                {
                  "username": "user_test",
                  "password": "123456",
                  "email": "not-an-email",
                  "code": "123456"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4003))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));

        verify(authService, never()).register(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void login_shouldInvokeTurnstileValidation_beforeAuthService() throws Exception {
        String payload = """
                {
                  "loginType": "password",
                  "account": "tester",
                  "password": "123456",
                  "cf-turnstile-response": "ts-token"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.10")
                        .content(payload))
                .andExpect(status().isOk());

        verify(turnstileService).verifyLoginToken("ts-token", "203.0.113.10");
        verify(authService).login(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("203.0.113.10"));
    }

    @Test
    void login_shouldSkipTurnstileValidation_forLocalPreviewWhenEnabled() throws Exception {
        TurnstileProperties turnstileProperties = new TurnstileProperties();
        turnstileProperties.setAllowLocalPreviewSkip(true);
        AuthController authController = new AuthController(
                authService,
                turnstileService,
                turnstileProperties,
                verificationCodeService,
                userService,
                jwtUtil);
        mockMvc = ControllerTestSupport.standaloneWithValidation(authController);

        String payload = """
                {
                  "loginType": "password",
                  "account": "tester",
                  "password": "123456"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "http://localhost:5173")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        })
                        .content(payload))
                .andExpect(status().isOk());

        verifyNoInteractions(turnstileService);
        verify(authService).login(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("127.0.0.1"));
    }
}
