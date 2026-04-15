package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.controller.PostController;
import com.campus.trend.campus_pulse.dto.request.PostCreateReq;
import com.campus.trend.campus_pulse.service.PostService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostControllerValidationTest {

    @Mock
    private PostService postService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        PostController postController = new PostController(postService);
        mockMvc = ControllerTestSupport.standaloneWithValidation(postController);
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        ControllerTestSupport.clearSecurity();
    }

    @Test
    void createPost_shouldReturnValidateError_whenTitleAndContentTooShort() throws Exception {
        String payload = """
                {
                  "sectionId": 1,
                  "title": "abc",
                  "content": "内容太短",
                  "tags": "测试"
                }
                """;

        mockMvc.perform(post("/post/create-post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4003))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("title: 文章题目需超过3个字符且不超过100个字符")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("content: 文章内容需超过30个字符")));

        verifyNoInteractions(postService);
    }

    @Test
    void createPost_shouldCallService_whenPayloadValidAndUserAuthenticated() throws Exception {
        ControllerTestSupport.mockLoginUser("10001");

        String payload = """
                {
                  "sectionId": 1,
                  "title": "这是一个合格的帖子标题",
                  "content": "这里是一段超过三十个字符的帖子正文内容，用于测试接口正常调用逻辑。",
                  "tags": "测试,校验"
                }
                """;

        mockMvc.perform(post("/post/create-post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000));

        ArgumentCaptor<PostCreateReq> reqCaptor = ArgumentCaptor.forClass(PostCreateReq.class);
        verify(postService, times(1)).createPost(reqCaptor.capture(), eq("10001"));
        assertThat(reqCaptor.getValue().getTitle()).isEqualTo("这是一个合格的帖子标题");
    }
}
