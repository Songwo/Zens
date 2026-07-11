package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.entity.OpsJobRun;
import com.campus.trend.campus_pulse.mapper.OpsJobRunMapper;
import com.campus.trend.campus_pulse.service.OpsAuditService;
import com.campus.trend.campus_pulse.utils.IdUtils;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OpsAuditServiceImpl implements OpsAuditService {
  private final OpsJobRunMapper jobMapper;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public OpsJobRun startPublish(
      String idempotencyKey, String draftId, String serviceId, String operatorId) {
    OpsJobRun job = new OpsJobRun();
    job.setId(IdUtils.genId("OPSJ"));
    job.setJobType("PUBLISH");
    job.setIdempotencyKey(idempotencyKey);
    job.setDraftId(draftId);
    job.setServiceId(serviceId);
    job.setOperatorId(operatorId);
    job.setStatus("RUNNING");
    job.setStartedAt(LocalDateTime.now());
    jobMapper.insert(job);
    return job;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void finishPublish(String jobId, String status, String errorMessage) {
    OpsJobRun job = jobMapper.selectById(jobId);
    if (job == null) {
      return;
    }
    job.setStatus(status);
    job.setErrorMessage(errorMessage);
    job.setFinishedAt(LocalDateTime.now());
    jobMapper.updateById(job);
  }
}
