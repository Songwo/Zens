package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.controller.TagController;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.filter.ApiPrefixRewriteFilter;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.service.UserTagRelationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiPrefixRewriteFilterTest {

    @Mock
    private TagService tagService;
    @Mock
    private UserTagRelationService userTagRelationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TagController(tagService, userTagRelationService))
                .addFilters(new ApiPrefixRewriteFilter())
                .build();
    }

    @Test
    void shouldRewriteApiPrefixForTagHotRoute() throws Exception {
        Tag tag = new Tag();
        tag.setId(1L);
        tag.setName("测试标签");
        when(tagService.getHotTags(eq(12))).thenReturn(List.of(tag));

        mockMvc.perform(get("/api/tag/hot").param("limit", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.data[0].name").value("测试标签"));

        verify(tagService, times(1)).getHotTags(12);
    }

    @Test
    void shouldKeepPlainRouteWorking() throws Exception {
        when(tagService.getHotTags(eq(8))).thenReturn(List.of());

        mockMvc.perform(get("/tag/hot").param("limit", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000));

        verify(tagService, times(1)).getHotTags(8);
    }
}
