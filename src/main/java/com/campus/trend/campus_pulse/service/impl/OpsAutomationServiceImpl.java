package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.config.properties.OpsAutomationProperties;
import com.campus.trend.campus_pulse.dto.request.*;
import com.campus.trend.campus_pulse.dto.response.PostResp;
import com.campus.trend.campus_pulse.entity.*;
import com.campus.trend.campus_pulse.mapper.*;
import com.campus.trend.campus_pulse.service.*;
import com.campus.trend.campus_pulse.utils.IdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OpsAutomationServiceImpl implements OpsAutomationService {
  private static final String CREATED = "CREATED",
      PENDING = "PENDING_APPROVAL",
      APPROVED = "APPROVED",
      PUBLISHED = "PUBLISHED",
      REJECTED = "REJECTED";
  private static final String CIRCUIT_KEY = "ops:control:circuit",
      CIRCUIT_REASON_KEY = "ops:control:circuit:reason",
      CIRCUIT_AT_KEY = "ops:control:circuit:at";
  private final OpsDraftMapper draftMapper;
  private final OpsContentPlanMapper planMapper;
  private final OpsApprovalMapper approvalMapper;
  private final OpsJobRunMapper jobMapper;
  private final OpsMetricSnapshotMapper metricMapper;
  private final UserMapper userMapper;
  private final OpsDraftStateMapper draftStateMapper;
  private final OpsAuditService opsAuditService;
  private final PostService postService;
  private final CommentService commentService;
  private final StringRedisTemplate redis;
  private final OpsAutomationProperties props;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public OpsDraft createDraft(OpsDraftCreateReq r, String sid) {
    requireService(sid);
    validateJson(r.getMetadataJson());
    OpsDraft old = findDraftByIdem(r.getIdempotencyKey());
    if (old != null) return old;
    if ("POST".equals(r.getType())
        && (!StringUtils.hasText(r.getTitle()) || r.getSectionId() == null))
      throw bad("帖子草稿必须包含标题和板块");
    if ("COMMENT".equals(r.getType())
        && (!StringUtils.hasText(r.getTargetPostId()) || r.getContent().length() > 2000))
      throw bad("评论草稿必须指定目标帖子且正文不超过2000字");
    if (StringUtils.hasText(r.getPlanId()) && planMapper.selectById(r.getPlanId()) == null)
      throw bad("关联的内容计划不存在");
    OpsDraft d = new OpsDraft();
    d.setId(IdUtils.genId("OPSD"));
    d.setIdempotencyKey(r.getIdempotencyKey());
    d.setPlanId(r.getPlanId());
    d.setType(r.getType());
    d.setStatus(CREATED);
    d.setTitle(r.getTitle());
    d.setContent(r.getContent());
    d.setSectionId(r.getSectionId());
    d.setTags(r.getTags());
    d.setCoverImage(r.getCoverImage());
    d.setTargetPostId(r.getTargetPostId());
    d.setParentCommentId(r.getParentCommentId());
    d.setMetadataJson(r.getMetadataJson());
    d.setSourceService(sid);
    d.setCreateTime(LocalDateTime.now());
    d.setUpdateTime(LocalDateTime.now());
    try {
      draftMapper.insert(d);
    } catch (DuplicateKeyException e) {
      return findDraftByIdem(r.getIdempotencyKey());
    }
    audit("CREATE_DRAFT", r.getIdempotencyKey(), d.getId(), sid, null, "SUCCESS", null);
    return d;
  }

  @Override
  @Transactional
  public OpsDraft submit(String id, String sid) {
    requireService(sid);
    OpsDraft d = need(id);
    if (PENDING.equals(d.getStatus())) return d;
    if (!CREATED.equals(d.getStatus())) throw bad("只有 CREATED 草稿可提交审批");
    validateReady(d);
    d.setStatus(PENDING);
    d.setUpdateTime(LocalDateTime.now());
    draftMapper.updateById(d);
    audit("SUBMIT_APPROVAL", null, id, sid, null, "SUCCESS", null);
    return d;
  }

  @Override
  public IPage<OpsDraft> list(String status, int page, int size) {
    LambdaQueryWrapper<OpsDraft> q =
        new LambdaQueryWrapper<OpsDraft>().orderByDesc(OpsDraft::getCreateTime);
    if (StringUtils.hasText(status)) q.eq(OpsDraft::getStatus, status);
    return draftMapper.selectPage(
        new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), q);
  }

  @Override
  @Transactional
  public OpsDraft approve(String id, String op, String note) {
    requireOperator(op);
    OpsDraft d = need(id);
    if (APPROVED.equals(d.getStatus())) return d;
    if (!PENDING.equals(d.getStatus())) throw bad("只有待审批草稿可批准");
    d.setStatus(APPROVED);
    d.setApprovedBy(op);
    d.setApprovedAt(LocalDateTime.now());
    d.setUpdateTime(LocalDateTime.now());
    draftMapper.updateById(d);
    approval(id, "APPROVE", op, note);
    audit("APPROVE", null, id, null, op, "SUCCESS", null);
    return d;
  }

  @Override
  @Transactional
  public OpsDraft reject(String id, String op, String note) {
    requireOperator(op);
    OpsDraft d = need(id);
    if (REJECTED.equals(d.getStatus())) return d;
    if (!PENDING.equals(d.getStatus())) throw bad("只有待审批草稿可拒绝");
    d.setStatus(REJECTED);
    d.setFailureReason(note);
    d.setUpdateTime(LocalDateTime.now());
    draftMapper.updateById(d);
    approval(id, "REJECT", op, note);
    audit("REJECT", null, id, null, op, "SUCCESS", null);
    return d;
  }

  @Override
  @Transactional
  public OpsDraft publish(String id, String idem, String sid) {
    requireService(sid);
    if (!props.isAutoPublish()) {
      throw bad("自动发布已关闭，必须由管理员人工发布");
    }
    return publishCore(id, idem, sid, null);
  }

  @Override
  @Transactional
  public OpsDraft publishByAdmin(String id, String idem, String operator) {
    requireOperator(operator);
    return publishCore(id, idem, null, operator);
  }

  private OpsDraft publishCore(String id, String idem, String sid, String operator) {
    OpsDraft d = need(id);
    OpsJobRun existing = findJob(idem);
    if (existing != null) {
      if ("SUCCESS".equals(existing.getStatus()) && PUBLISHED.equals(d.getStatus())) return d;
      throw bad("该幂等键已有未成功的发布任务，请更换幂等键并人工复核");
    }
    if (PUBLISHED.equals(d.getStatus())) return d;
    if (!APPROVED.equals(d.getStatus())) throw bad("草稿尚未人工批准");
    if (isCircuitOpen()) throw bad("运营写入熔断已开启");
    validateReady(d);
    if (draftStateMapper.claimForPublish(id) != 1) {
      OpsDraft latest = need(id);
      if (PUBLISHED.equals(latest.getStatus())) return latest;
      throw bad("草稿正在发布或状态已变化，请勿重复提交");
    }
    d.setStatus("PUBLISHING");
    User u = author();
    boolean reply = "COMMENT".equals(d.getType());
    OpsJobRun run = opsAuditService.startPublish(idem, id, sid, operator);
    boolean limitConsumed = false;
    try {
      consumeLimit(reply);
      limitConsumed = true;
      if (reply) {
        CommentCreateReq c = new CommentCreateReq();
        c.setPostId(d.getTargetPostId());
        c.setContent(d.getContent());
        c.setParentId(StringUtils.hasText(d.getParentCommentId()) ? d.getParentCommentId() : "0");
        commentService.addComment(c, u.getId());
      } else {
        PostDraftReq p = new PostDraftReq();
        p.setTitle(d.getTitle());
        p.setContent(d.getContent());
        p.setSectionId(d.getSectionId());
        p.setTags(d.getTags());
        p.setCoverImage(d.getCoverImage());
        PostResp saved = postService.saveDraft(p, u.getId());
        d.setPostId(saved.getId());
        PostUpdateReq up = new PostUpdateReq();
        up.setPostId(saved.getId());
        up.setPublish(true);
        postService.updatePost(up, u.getId());
        // 草稿已通过运营人工审批，发布后同步为普通内容治理的 APPROVED，避免双重状态不一致。
        postService.approvePost(saved.getId(), u.getId());
      }
      d.setStatus(PUBLISHED);
      d.setPublishedAt(LocalDateTime.now());
      d.setUpdateTime(LocalDateTime.now());
      draftMapper.updateById(d);
      opsAuditService.finishPublish(run.getId(), "SUCCESS", null);
      return d;
    } catch (RuntimeException e) {
      if (limitConsumed) {
        redis.opsForValue().decrement(limitKey(reply));
      }
      opsAuditService.finishPublish(run.getId(), "FAILED", safe(e.getMessage()));
      throw e;
    }
  }

  @Override
  @Transactional
  public OpsContentPlan createPlan(OpsPlanCreateReq r, String sid) {
    requireService(sid);
    validateJson(r.getMetadataJson());
    OpsContentPlan old = findPlan(r.getIdempotencyKey());
    if (old != null) return old;
    OpsContentPlan p = new OpsContentPlan();
    p.setId(IdUtils.genId("OPSP"));
    p.setIdempotencyKey(r.getIdempotencyKey());
    p.setTopic(r.getTopic());
    p.setTitle(r.getTitle());
    p.setBrief(r.getBrief());
    p.setScheduledAt(r.getScheduledAt());
    p.setMetadataJson(r.getMetadataJson());
    p.setStatus("PLANNED");
    p.setCreatedBy(sid);
    p.setCreateTime(LocalDateTime.now());
    p.setUpdateTime(LocalDateTime.now());
    try {
      planMapper.insert(p);
    } catch (DuplicateKeyException e) {
      return findPlan(r.getIdempotencyKey());
    }
    audit("CREATE_PLAN", r.getIdempotencyKey(), null, sid, null, "SUCCESS", null);
    return p;
  }

  @Override
  @Transactional
  public OpsMetricSnapshot recordMetric(OpsMetricReq r, String sid) {
    requireService(sid);
    OpsMetricSnapshot old = findMetric(r.getIdempotencyKey());
    if (old != null) return old;
    if (!r.getPeriodEnd().isAfter(r.getPeriodStart())) throw bad("periodEnd 必须晚于 periodStart");
    validateJson(r.getMetricsJson());
    OpsMetricSnapshot m = new OpsMetricSnapshot();
    m.setId(IdUtils.genId("OPSM"));
    m.setIdempotencyKey(r.getIdempotencyKey());
    m.setPeriodStart(r.getPeriodStart());
    m.setPeriodEnd(r.getPeriodEnd());
    m.setMetricsJson(r.getMetricsJson());
    m.setSourceService(sid);
    m.setCreateTime(LocalDateTime.now());
    try {
      metricMapper.insert(m);
    } catch (DuplicateKeyException e) {
      return findMetric(r.getIdempotencyKey());
    }
    audit("METRIC_SNAPSHOT", r.getIdempotencyKey(), null, sid, null, "SUCCESS", null);
    return m;
  }

  @Override
  public Map<String, Object> status() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("enabled", true);
    m.put("circuitOpen", isCircuitOpen());
    m.put("circuitReason", redis.opsForValue().get(CIRCUIT_REASON_KEY));
    m.put("circuitUpdatedAt", redis.opsForValue().get(CIRCUIT_AT_KEY));
    m.put("autoPublish", false);
    m.put("approvalPolicy", "ALL_MANUAL");
    m.put("firstApprovalCount", props.getFirstApprovalCount());
    m.put("todayPublishCount", count(false));
    m.put("todayReplyCount", count(true));
    m.put("dailyPublishLimit", props.getDailyPublishLimit());
    m.put("dailyReplyLimit", props.getDailyReplyLimit());
    return m;
  }

  @Override
  public Map<String, Object> setCircuit(boolean open, String reason, String op) {
    redis.opsForValue().set(CIRCUIT_KEY, String.valueOf(open));
    redis.opsForValue().set(CIRCUIT_REASON_KEY, StringUtils.hasText(reason) ? reason : "");
    redis.opsForValue().set(CIRCUIT_AT_KEY, LocalDateTime.now().toString());
    audit("CIRCUIT", null, null, null, op, "SUCCESS", "{\"open\":" + open + "}");
    return status();
  }

  private void requireService(String sid) {
    if (!props.getServiceId().equals(sid))
      throw new BusinessException(ResultCode.NO_PERMISSION, "仅允许 Zens 运营服务调用");
  }

  private User author() {
    User u =
        userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, props.getAuthorUsername())
                .last("LIMIT 1"));
    if (u == null || !Integer.valueOf(1).equals(u.getStatus()))
      throw new BusinessException(ResultCode.NO_PERMISSION, "运营作者账号不存在或已禁用");
    return u;
  }

  private boolean isCircuitOpen() {
    String v = redis.opsForValue().get(CIRCUIT_KEY);
    return v == null ? props.isCircuitOpen() : Boolean.parseBoolean(v);
  }

  private String limitKey(boolean reply) {
    return "ops:limit:" + (reply ? "reply" : "publish") + ":" + LocalDate.now();
  }

  private long count(boolean reply) {
    String v = redis.opsForValue().get(limitKey(reply));
    try {
      return v == null ? 0 : Long.parseLong(v);
    } catch (Exception e) {
      return 0;
    }
  }

  private void consumeLimit(boolean reply) {
    String k = limitKey(reply);
    Long n = redis.opsForValue().increment(k);
    if (n != null && n == 1) redis.expire(k, 2, TimeUnit.DAYS);
    int max = reply ? props.getDailyReplyLimit() : props.getDailyPublishLimit();
    if (n == null || n > max) {
      redis.opsForValue().decrement(k);
      throw bad("已达到今日" + (reply ? "回复" : "发布") + "限额");
    }
  }

  private OpsDraft need(String id) {
    OpsDraft d = draftMapper.selectById(id);
    if (d == null) throw bad("运营草稿不存在");
    return d;
  }

  private OpsDraft findDraftByIdem(String k) {
    return draftMapper.selectOne(
        new LambdaQueryWrapper<OpsDraft>().eq(OpsDraft::getIdempotencyKey, k));
  }

  private OpsContentPlan findPlan(String k) {
    return planMapper.selectOne(
        new LambdaQueryWrapper<OpsContentPlan>().eq(OpsContentPlan::getIdempotencyKey, k));
  }

  private OpsMetricSnapshot findMetric(String k) {
    return metricMapper.selectOne(
        new LambdaQueryWrapper<OpsMetricSnapshot>().eq(OpsMetricSnapshot::getIdempotencyKey, k));
  }

  private OpsJobRun findJob(String k) {
    return jobMapper.selectOne(
        new LambdaQueryWrapper<OpsJobRun>().eq(OpsJobRun::getIdempotencyKey, k));
  }

  private void approval(String id, String action, String op, String note) {
    OpsApproval a = new OpsApproval();
    a.setId(IdUtils.genId("OPSA"));
    a.setDraftId(id);
    a.setAction(action);
    a.setOperatorId(op);
    a.setNote(note);
    a.setCreateTime(LocalDateTime.now());
    approvalMapper.insert(a);
  }

  private OpsJobRun audit(
      String type, String idem, String draft, String sid, String op, String status, String detail) {
    OpsJobRun j = new OpsJobRun();
    j.setId(IdUtils.genId("OPSJ"));
    j.setJobType(type);
    j.setIdempotencyKey(idem);
    j.setDraftId(draft);
    j.setServiceId(sid);
    j.setOperatorId(op);
    j.setStatus(status);
    j.setDetailJson(detail);
    j.setStartedAt(LocalDateTime.now());
    if (!"RUNNING".equals(status)) j.setFinishedAt(LocalDateTime.now());
    jobMapper.insert(j);
    return j;
  }

  private void finish(OpsJobRun j, String s, String e) {
    j.setStatus(s);
    j.setErrorMessage(e);
    j.setFinishedAt(LocalDateTime.now());
    jobMapper.updateById(j);
  }

  private void validateReady(OpsDraft d) {
    if ("COMMENT".equals(d.getType())) {
      if (!StringUtils.hasText(d.getTargetPostId())
          || d.getContent() == null
          || d.getContent().length() > 2000) throw bad("评论草稿不完整或超过2000字");
      return;
    }
    if (!StringUtils.hasText(d.getTitle())
        || d.getTitle().trim().length() < 4
        || d.getTitle().trim().length() > 100
        || d.getContent() == null
        || d.getContent().length() < 31
        || d.getSectionId() == null
        || !StringUtils.hasText(d.getTags())) throw bad("帖子草稿需满足标题4-100字、正文至少31字、板块和标签必填");
  }

  private void requireOperator(String op) {
    if (!StringUtils.hasText(op)) throw new BusinessException(ResultCode.NO_PERMISSION, "管理员身份无效");
  }

  private void validateJson(String json) {
    if (!StringUtils.hasText(json)) return;
    try {
      objectMapper.readTree(json);
    } catch (Exception e) {
      throw bad("JSON 字段格式错误");
    }
  }

  private BusinessException bad(String s) {
    return new BusinessException(ResultCode.PARAM_ERROR, s);
  }

  private String safe(String s) {
    return s == null ? null : s.substring(0, Math.min(s.length(), 500));
  }
}
