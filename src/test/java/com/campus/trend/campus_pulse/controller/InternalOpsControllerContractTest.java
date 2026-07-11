package com.campus.trend.campus_pulse.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.trend.campus_pulse.config.properties.OpsAutomationProperties;
import com.campus.trend.campus_pulse.dto.request.OpsDraftCreateReq;
import com.campus.trend.campus_pulse.dto.request.OpsMetricReq;
import com.campus.trend.campus_pulse.entity.OpsDraft;
import com.campus.trend.campus_pulse.entity.OpsMetricSnapshot;
import com.campus.trend.campus_pulse.service.OpsAutomationService;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InternalOpsControllerContractTest {

  @Mock private OpsAutomationService service;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    OpsAutomationProperties properties = new OpsAutomationProperties();
    properties.setServiceId("zens-ops");
    mvc = MockMvcBuilders.standaloneSetup(new InternalOpsController(service, properties)).build();
  }

  @Test
  void draftEndpointAcceptsStringPlanIdAndCommaSeparatedTags() throws Exception {
    OpsDraft saved = new OpsDraft();
    saved.setId("OPSD_1");
    saved.setStatus("CREATED");
    when(service.createDraft(org.mockito.ArgumentMatchers.any(), eq("zens-ops"))).thenReturn(saved);

    mvc.perform(
            post("/api/internal/ops/drafts")
                .requestAttr("internal.serviceId", "zens-ops")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "idempotencyKey":"zens-ops:post:12345678",
                      "planId":"OPSP_42",
                      "type":"POST",
                      "title":"一篇长期内容",
                      "content":"这是一段足够长并且等待人工审批的原创 Markdown 草稿正文。",
                      "sectionId":1,
                      "tags":"长期表达,社区",
                      "metadataJson":"{}"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id", is("OPSD_1")))
        .andExpect(jsonPath("$.data.status", is("CREATED")));

    ArgumentCaptor<OpsDraftCreateReq> request = ArgumentCaptor.forClass(OpsDraftCreateReq.class);
    verify(service).createDraft(request.capture(), eq("zens-ops"));
    org.junit.jupiter.api.Assertions.assertEquals("OPSP_42", request.getValue().getPlanId());
    org.junit.jupiter.api.Assertions.assertEquals("长期表达,社区", request.getValue().getTags());
  }

  @Test
  void metricsEndpointAcceptsLocalDateTimeWithoutOffset() throws Exception {
    OpsMetricSnapshot saved = new OpsMetricSnapshot();
    saved.setId("OPSM_1");
    when(service.recordMetric(org.mockito.ArgumentMatchers.any(), eq("zens-ops")))
        .thenReturn(saved);

    mvc.perform(
            post("/api/internal/ops/metrics")
                .requestAttr("internal.serviceId", "zens-ops")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "idempotencyKey":"zens-ops:metrics:12345678",
                      "periodStart":"2026-07-05T12:00:00",
                      "periodEnd":"2026-07-12T12:00:00",
                      "metricsJson":"{\\\"site\\\":{\\\"users\\\":1}}"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id", is("OPSM_1")));

    ArgumentCaptor<OpsMetricReq> request = ArgumentCaptor.forClass(OpsMetricReq.class);
    verify(service).recordMetric(request.capture(), eq("zens-ops"));
    org.junit.jupiter.api.Assertions.assertEquals(
        LocalDateTime.of(2026, 7, 5, 12, 0), request.getValue().getPeriodStart());
  }

  @Test
  void statusAndPublishPathsMatchMainSiteClientContract() throws Exception {
    when(service.status()).thenReturn(Map.of("circuitOpen", true, "todayPublishCount", 0));
    OpsDraft published = new OpsDraft();
    published.setId("OPSD_1");
    published.setStatus("PUBLISHED");
    when(service.publish("OPSD_1", "zens-ops:publish:12345678", "zens-ops")).thenReturn(published);

    mvc.perform(get("/api/internal/ops/status").requestAttr("internal.serviceId", "zens-ops"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.circuitOpen", is(true)));

    mvc.perform(
            post("/api/internal/ops/drafts/OPSD_1/publish")
                .requestAttr("internal.serviceId", "zens-ops")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"zens-ops:publish:12345678\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", is("PUBLISHED")));

    verify(service).publish("OPSD_1", "zens-ops:publish:12345678", "zens-ops");
  }
}
