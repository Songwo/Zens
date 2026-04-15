package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.controller.UploadController;
import com.campus.trend.campus_pulse.service.impl.UploadFileServiceImpl;
import com.campus.trend.campus_pulse.service.VideoTranscodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UploadControllerValidationTest {

    @Mock
    private VideoTranscodeService videoTranscodeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UploadFileServiceImpl uploadFileService = new UploadFileServiceImpl(videoTranscodeService);
        ReflectionTestUtils.setField(uploadFileService, "urlPrefix", "/uploads/");
        ReflectionTestUtils.setField(uploadFileService, "avatarModule", "avatar");
        ReflectionTestUtils.setField(uploadFileService, "maxImageSizeMb", 5L);
        ReflectionTestUtils.setField(uploadFileService, "allowedImageExtensionsRaw", ".jpg,.jpeg,.png,.gif,.webp");
        ReflectionTestUtils.setField(uploadFileService, "maxImageWidth", 1920);
        ReflectionTestUtils.setField(uploadFileService, "maxImageHeight", 1080);
        ReflectionTestUtils.setField(uploadFileService, "imageJpegQuality", 0.85f);
        ReflectionTestUtils.setField(uploadFileService, "maxVideoSizeMb", 80L);
        ReflectionTestUtils.setField(uploadFileService, "allowedVideoExtensionsRaw", ".mp4,.webm,.ogg,.mov");
        ReflectionTestUtils.setField(uploadFileService, "enableMalwarePlaceholderScan", true);

        UploadController uploadController = new UploadController(uploadFileService);

        mockMvc = ControllerTestSupport.standaloneWithValidation(uploadController);
    }

    @Test
    void uploadImage_shouldRejectWrongContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "plain text".getBytes()
        );

        mockMvc.perform(multipart("/common/upload/image")
                        .file(file)
                        .param("module", "post"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5006))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("只能上传图片文件")));
    }

    @Test
    void uploadVideo_shouldRejectEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.mp4",
                "video/mp4",
                new byte[0]
        );

        mockMvc.perform(multipart("/common/upload/video")
                        .file(file)
                        .param("module", "post"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5004))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("文件不能为空")));
    }

    @Test
    void uploadImage_shouldRejectMimeMismatch() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.jpg",
                "image/png",
                "fake image".getBytes()
        );

        mockMvc.perform(multipart("/common/upload/image")
                        .file(file)
                        .param("module", "post"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5006))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("扩展名与MIME类型不匹配")));
    }
}
