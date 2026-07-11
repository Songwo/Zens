package com.campus.trend.campus_pulse.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.campus.trend.campus_pulse.config.properties.OpsAutomationProperties;
import com.campus.trend.campus_pulse.dto.request.*;
import com.campus.trend.campus_pulse.entity.*;
import com.campus.trend.campus_pulse.mapper.*;
import com.campus.trend.campus_pulse.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class OpsAutomationServiceImplTest {
  @Mock OpsDraftMapper drafts;
  @Mock OpsContentPlanMapper plans;
  @Mock OpsApprovalMapper approvals;
  @Mock OpsJobRunMapper jobs;
  @Mock OpsMetricSnapshotMapper metrics;
  @Mock UserMapper users;
  @Mock OpsDraftStateMapper draftStates;
  @Mock OpsAuditService opsAuditService;
  @Mock PostService posts;
  @Mock CommentService comments;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> values;
  OpsAutomationServiceImpl service;

  @BeforeEach
  void init() {
    lenient().when(redis.opsForValue()).thenReturn(values);
    OpsAutomationProperties p = new OpsAutomationProperties();
    service =
        new OpsAutomationServiceImpl(
            drafts,
            plans,
            approvals,
            jobs,
            metrics,
            users,
            draftStates,
            opsAuditService,
            posts,
            comments,
            redis,
            p,
            new ObjectMapper());
  }

  @Test
  void rejectsWrongService() {
    OpsDraftCreateReq r = req();
    assertThrows(RuntimeException.class, () -> service.createDraft(r, "not-ops"));
    verifyNoInteractions(drafts);
  }

  @Test
  void createIsIdempotent() {
    OpsDraft existing = new OpsDraft();
    existing.setId("D1");
    when(drafts.selectOne(any())).thenReturn(existing);
    assertSame(existing, service.createDraft(req(), "zens-ops"));
    verify(drafts, never()).insert(any(OpsDraft.class));
  }

  @Test
  void approvalRequiresPendingState() {
    OpsDraft d = new OpsDraft();
    d.setId("D1");
    d.setStatus("CREATED");
    when(drafts.selectById("D1")).thenReturn(d);
    assertThrows(RuntimeException.class, () -> service.approve("D1", "admin", null));
    verify(approvals, never()).insert(any(OpsApproval.class));
  }

  @Test
  void approvingPendingWritesAuditAndApproval() {
    OpsDraft d = new OpsDraft();
    d.setId("D1");
    d.setStatus("PENDING_APPROVAL");
    when(drafts.selectById("D1")).thenReturn(d);
    OpsDraft out = service.approve("D1", "admin", "ok");
    assertEquals("APPROVED", out.getStatus());
    verify(approvals).insert(any(OpsApproval.class));
    verify(jobs).insert(any(OpsJobRun.class));
  }

  @Test
  void adminPublishRejectsUnapprovedDraft() {
    OpsDraft d = ready("PENDING_APPROVAL");
    when(drafts.selectById("D1")).thenReturn(d);
    assertThrows(
        RuntimeException.class, () -> service.publishByAdmin("D1", "publish-001", "admin"));
    verifyNoInteractions(posts, comments);
  }

  @Test
  void circuitBlocksAdminPublish() {
    OpsDraft d = ready("APPROVED");
    when(drafts.selectById("D1")).thenReturn(d);
    when(values.get("ops:control:circuit")).thenReturn("true");
    assertThrows(
        RuntimeException.class, () -> service.publishByAdmin("D1", "publish-002", "admin"));
    verifyNoInteractions(posts, comments);
  }

  @Test
  void publishCasPreventsConcurrentDuplicate() {
    OpsDraft d = ready("APPROVED");
    when(drafts.selectById("D1")).thenReturn(d);
    when(values.get("ops:control:circuit")).thenReturn("false");
    when(draftStates.claimForPublish("D1")).thenReturn(0);
    assertThrows(
        RuntimeException.class, () -> service.publishByAdmin("D1", "publish-003", "admin"));
    verifyNoInteractions(posts, comments);
  }

  @Test
  void publishFailureReturnsLimitAndPersistsFailedAuditThroughSeparateService() {
    OpsDraft d = ready("APPROVED");
    d.setType("COMMENT");
    d.setTargetPostId("POST_1");
    d.setContent("A safe reply draft");
    User author = new User();
    author.setId("OPS_USER");
    author.setStatus(1);
    OpsJobRun run = new OpsJobRun();
    run.setId("JOB_1");
    when(drafts.selectById("D1")).thenReturn(d);
    when(values.get("ops:control:circuit")).thenReturn("false");
    when(draftStates.claimForPublish("D1")).thenReturn(1);
    when(users.selectOne(any())).thenReturn(author);
    when(opsAuditService.startPublish("publish-004", "D1", null, "admin")).thenReturn(run);
    when(values.increment(anyString())).thenReturn(1L);
    doThrow(new RuntimeException("comment write failed"))
        .when(comments)
        .addComment(any(CommentCreateReq.class), eq("OPS_USER"));

    assertThrows(
        RuntimeException.class, () -> service.publishByAdmin("D1", "publish-004", "admin"));

    verify(values).decrement(contains("ops:limit:reply:"));
    verify(opsAuditService).finishPublish("JOB_1", "FAILED", "comment write failed");
  }

  private OpsDraftCreateReq req() {
    OpsDraftCreateReq r = new OpsDraftCreateReq();
    r.setIdempotencyKey("idem-key-1");
    r.setType("POST");
    r.setTitle("title");
    r.setContent("long original content");
    r.setSectionId(1L);
    return r;
  }

  private OpsDraft ready(String status) {
    OpsDraft d = new OpsDraft();
    d.setId("D1");
    d.setStatus(status);
    d.setType("POST");
    d.setTitle("Valid title");
    d.setContent("This is sufficiently long original Markdown content for publishing.");
    d.setSectionId(1L);
    d.setTags("community");
    return d;
  }
}
