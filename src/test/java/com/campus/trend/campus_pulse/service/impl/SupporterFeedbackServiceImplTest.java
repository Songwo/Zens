package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.request.SupporterFeedbackCreateReq;
import com.campus.trend.campus_pulse.dto.request.SupporterFeedbackReplyReq;
import com.campus.trend.campus_pulse.entity.SupporterEntitlement;
import com.campus.trend.campus_pulse.entity.SupporterFeedback;
import com.campus.trend.campus_pulse.mapper.SupporterEntitlementMapper;
import com.campus.trend.campus_pulse.mapper.SupporterFeedbackMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupporterFeedbackServiceImplTest {
    @Mock private SupporterFeedbackMapper feedbackMapper;
    @Mock private SupporterEntitlementMapper entitlementMapper;
    private SupporterFeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        initTable(SupporterFeedback.class, "supporter-feedback-test");
        initTable(SupporterEntitlement.class, "supporter-entitlement-feedback-test");
        service = new SupporterFeedbackServiceImpl(feedbackMapper, entitlementMapper);
    }

    @Test
    void create_shouldPersistOpenFeedbackForActivePlusSupporter() {
        when(entitlementMapper.selectOne(any())).thenReturn(activeEntitlement("supporter_plus_30"));
        ArgumentCaptor<SupporterFeedback> feedback = ArgumentCaptor.forClass(SupporterFeedback.class);

        var result = service.create("user-1", new SupporterFeedbackCreateReq(
                "  希望改善移动端编辑器  ", "  小屏幕下工具栏会遮挡正文，希望能够自动收起。  "));

        verify(feedbackMapper).insert(feedback.capture());
        assertThat(feedback.getValue().getStatus()).isEqualTo("OPEN");
        assertThat(feedback.getValue().getSubject()).isEqualTo("希望改善移动端编辑器");
        assertThat(result.userId()).isEqualTo("user-1");
        assertThat(result.status()).isEqualTo("OPEN");
    }

    @Test
    void create_shouldRejectOrdinarySupporter() {
        when(entitlementMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.create("user-2", new SupporterFeedbackCreateReq(
                "普通支持者反馈", "这是一条长度足够但没有共建权益的反馈内容。")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getResultCode()).isEqualTo(ResultCode.NO_PERMISSION));
    }

    @Test
    void reply_shouldRecordAdminReplyAndAnsweredStatus() {
        SupporterFeedback feedback = SupporterFeedback.builder()
                .id(7L).userId("user-1").subject("主题").content("内容")
                .status("OPEN").createdAt(LocalDateTime.now().minusDays(1)).build();
        when(feedbackMapper.selectById(7L)).thenReturn(feedback);

        var result = service.reply(7L, "admin-1", new SupporterFeedbackReplyReq(" 已进入近期优化计划。 ", null));

        verify(feedbackMapper).updateById(feedback);
        assertThat(result.status()).isEqualTo("ANSWERED");
        assertThat(result.adminReply()).isEqualTo("已进入近期优化计划。");
        assertThat(result.repliedBy()).isEqualTo("admin-1");
        assertThat(result.repliedAt()).isNotNull();
    }

    private SupporterEntitlement activeEntitlement(String planCode) {
        return SupporterEntitlement.builder().userId("user-1").planCode(planCode).status("ACTIVE")
                .startsAt(LocalDateTime.now().minusDays(1)).expiresAt(LocalDateTime.now().plusDays(29)).build();
    }

    private void initTable(Class<?> entity, String resource) {
        if (TableInfoHelper.getTableInfo(entity) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), resource), entity);
        }
    }
}
