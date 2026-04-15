package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.controller.ModeratorApplicationController;
import com.campus.trend.campus_pulse.service.ModeratorApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ModeratorApplicationControllerTest {

    @Mock
    private ModeratorApplicationService moderatorApplicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.standaloneWithValidation(
                new ModeratorApplicationController(moderatorApplicationService));
    }

    @AfterEach
    void tearDown() {
        ControllerTestSupport.clearSecurity();
    }

    @Test
    void apply_shouldRejectShortReason() throws Exception {
        ControllerTestSupport.mockLoginUser("u-1", "tester", "ROLE_USER");

        mockMvc.perform(post("/moderator/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sectionId": 1,
                                  "reason": "too short"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4003))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("申请理由长度需在 10 到 500 个字符之间")));

        verifyNoInteractions(moderatorApplicationService);
    }

    @Test
    void applications_shouldRejectNonAdminUser() throws Exception {
        ControllerTestSupport.mockLoginUser("u-2", "tester", "ROLE_USER");

        mockMvc.perform(get("/moderator/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3003))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("仅管理员可执行该操作")));

        verifyNoInteractions(moderatorApplicationService);
    }

    @Test
    void approve_shouldReadReviewNoteFromRequestBody() throws Exception {
        ControllerTestSupport.mockLoginUser("admin-1", "admin", "ROLE_ADMIN");

        mockMvc.perform(post("/moderator/approve/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewNote": "欢迎加入版主团队"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000));

        verify(moderatorApplicationService).approve(12L, "欢迎加入版主团队");
    }

    @Test
    void reject_shouldReadReviewNoteFromRequestBody() throws Exception {
        ControllerTestSupport.mockLoginUser("admin-1", "admin", "ROLE_ADMIN");

        mockMvc.perform(post("/moderator/reject/18")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewNote": "当前活跃度和管理经验说明不足"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000));

        verify(moderatorApplicationService).reject(18L, "当前活跃度和管理经验说明不足");
    }
}
