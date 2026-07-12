package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.request.SupporterFeedbackCreateReq;
import com.campus.trend.campus_pulse.dto.request.SupporterFeedbackReplyReq;
import com.campus.trend.campus_pulse.dto.response.SimplePageResp;
import com.campus.trend.campus_pulse.dto.response.SupporterFeedbackResp;
import com.campus.trend.campus_pulse.entity.SupporterEntitlement;
import com.campus.trend.campus_pulse.entity.SupporterFeedback;
import com.campus.trend.campus_pulse.mapper.SupporterEntitlementMapper;
import com.campus.trend.campus_pulse.mapper.SupporterFeedbackMapper;
import com.campus.trend.campus_pulse.service.SupporterFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SupporterFeedbackServiceImpl implements SupporterFeedbackService {
    static final String REQUIRED_PLAN = "supporter_plus_30";
    private static final int MAX_PAGE_SIZE = 50;

    private final SupporterFeedbackMapper feedbackMapper;
    private final SupporterEntitlementMapper entitlementMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupporterFeedbackResp create(String userId, SupporterFeedbackCreateReq request) {
        requirePlusEntitlement(userId);
        String subject = request.subject().trim();
        String content = request.content().trim();
        if (subject.length() < 4 || subject.length() > 100) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "反馈主题需为 4 到 100 个字符");
        }
        if (content.length() < 10 || content.length() > 2000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "反馈内容需为 10 到 2000 个字符");
        }
        LocalDateTime now = LocalDateTime.now();
        SupporterFeedback feedback = SupporterFeedback.builder()
                .userId(userId)
                .subject(subject)
                .content(content)
                .status("OPEN")
                .createdAt(now)
                .updatedAt(now)
                .build();
        feedbackMapper.insert(feedback);
        return toResp(feedback);
    }

    @Override
    public SimplePageResp<SupporterFeedbackResp> pageMine(String userId, int page, int pageSize) {
        requirePlusEntitlement(userId);
        return page(new LambdaQueryWrapper<SupporterFeedback>()
                .eq(SupporterFeedback::getUserId, userId)
                .orderByDesc(SupporterFeedback::getCreatedAt)
                .orderByDesc(SupporterFeedback::getId), page, pageSize);
    }

    @Override
    public SimplePageResp<SupporterFeedbackResp> pageAdmin(int page, int pageSize, String status, String userId) {
        LambdaQueryWrapper<SupporterFeedback> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if (!List.of("OPEN", "ANSWERED", "CLOSED").contains(normalized)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "反馈状态不合法");
            }
            wrapper.eq(SupporterFeedback::getStatus, normalized);
        }
        if (StringUtils.hasText(userId)) wrapper.eq(SupporterFeedback::getUserId, userId.trim());
        wrapper.orderByDesc(SupporterFeedback::getCreatedAt)
                .orderByDesc(SupporterFeedback::getId);
        return page(wrapper, page, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupporterFeedbackResp reply(Long id, String adminId, SupporterFeedbackReplyReq request) {
        SupporterFeedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) throw new BusinessException(ResultCode.PARAM_ERROR, "反馈记录不存在");
        String reply = request.reply().trim();
        if (reply.length() < 2 || reply.length() > 2000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "回复内容需为 2 到 2000 个字符");
        }
        LocalDateTime now = LocalDateTime.now();
        feedback.setAdminReply(reply);
        feedback.setStatus(StringUtils.hasText(request.status())
                ? request.status().trim().toUpperCase(Locale.ROOT) : "ANSWERED");
        feedback.setRepliedBy(adminId);
        feedback.setRepliedAt(now);
        feedback.setUpdatedAt(now);
        feedbackMapper.updateById(feedback);
        return toResp(feedback);
    }

    private SimplePageResp<SupporterFeedbackResp> page(LambdaQueryWrapper<SupporterFeedback> wrapper,
                                                       int page, int pageSize) {
        Page<SupporterFeedback> query = new Page<>(Math.max(1, page),
                Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE));
        Page<SupporterFeedback> result = feedbackMapper.selectPage(query, wrapper);
        return SimplePageResp.of(result.getRecords().stream().map(this::toResp).toList(), result.getTotal());
    }

    private void requirePlusEntitlement(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.TOKEN_MISSING, "请先登录后使用共建反馈通道");
        }
        LocalDateTime now = LocalDateTime.now();
        SupporterEntitlement entitlement = entitlementMapper.selectOne(
                new LambdaQueryWrapper<SupporterEntitlement>()
                        .eq(SupporterEntitlement::getUserId, userId)
                        .eq(SupporterEntitlement::getPlanCode, REQUIRED_PLAN)
                        .eq(SupporterEntitlement::getStatus, "ACTIVE")
                        .le(SupporterEntitlement::getStartsAt, now)
                        .gt(SupporterEntitlement::getExpiresAt, now)
                        .orderByDesc(SupporterEntitlement::getExpiresAt)
                        .last("LIMIT 1"));
        if (entitlement == null) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "仅有效的 Zens 共建支持者可使用此反馈通道");
        }
    }

    private SupporterFeedbackResp toResp(SupporterFeedback feedback) {
        return SupporterFeedbackResp.builder()
                .id(feedback.getId()).userId(feedback.getUserId())
                .subject(feedback.getSubject()).content(feedback.getContent())
                .status(feedback.getStatus()).adminReply(feedback.getAdminReply())
                .repliedBy(feedback.getRepliedBy()).repliedAt(feedback.getRepliedAt())
                .createdAt(feedback.getCreatedAt()).updatedAt(feedback.getUpdatedAt())
                .build();
    }
}
