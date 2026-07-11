package com.campus.trend.campus_pulse.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campus.trend.campus_pulse.entity.OpsJobRun;
import com.campus.trend.campus_pulse.mapper.OpsJobRunMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class OpsAuditServiceImplTest {
  @Mock private OpsJobRunMapper mapper;

  @Test
  void failureUpdateUsesRequiresNewAndPersistsFailedState() throws Exception {
    OpsJobRun job = new OpsJobRun();
    job.setId("JOB_1");
    job.setStatus("RUNNING");
    when(mapper.selectById("JOB_1")).thenReturn(job);
    OpsAuditServiceImpl service = new OpsAuditServiceImpl(mapper);

    service.finishPublish("JOB_1", "FAILED", "write failed");

    ArgumentCaptor<OpsJobRun> captor = ArgumentCaptor.forClass(OpsJobRun.class);
    verify(mapper).updateById(captor.capture());
    assertEquals("FAILED", captor.getValue().getStatus());
    assertEquals("write failed", captor.getValue().getErrorMessage());

    Method method =
        OpsAuditServiceImpl.class.getMethod(
            "finishPublish", String.class, String.class, String.class);
    Transactional transactional = method.getAnnotation(Transactional.class);
    assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
  }
}
