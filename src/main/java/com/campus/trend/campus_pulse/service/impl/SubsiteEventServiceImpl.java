package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.common.notification.NotificationType;
import com.campus.trend.campus_pulse.dto.request.SubsiteEventCreateReq;
import com.campus.trend.campus_pulse.dto.response.SimplePageResp;
import com.campus.trend.campus_pulse.dto.response.SubsiteEventResp;
import com.campus.trend.campus_pulse.entity.SubsiteEvent;
import com.campus.trend.campus_pulse.mapper.SubsiteEventMapper;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.SubsiteEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubsiteEventServiceImpl implements SubsiteEventService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SubsiteEventMapper subsiteEventMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final com.campus.trend.campus_pulse.monitor.EcosystemMetrics ecosystemMetrics;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubsiteEventResp record(SubsiteEventCreateReq req, String serviceId) {
        validateCaller(req, serviceId);

        SubsiteEvent existing = findByEventId(req.getEventId());
        if (existing != null) {
            return toResp(existing);
        }

        SubsiteEvent event = SubsiteEvent.builder()
                .eventId(req.getEventId())
                .source(req.getSource())
                .eventType(req.getEventType())
                .userId(trimToNull(req.getUserId()))
                .title(req.getTitle().trim())
                .content(req.getContent().trim())
                .relatedId(trimToNull(req.getRelatedId()))
                .severity(normalizeSeverity(req.getSeverity()))
                .status(StringUtils.hasText(req.getStatus()) ? req.getStatus().trim() : "recorded")
                .payloadJson(serializePayload(req))
                .createdAt(LocalDateTime.now())
                .build();

        try {
            subsiteEventMapper.insert(event);
        } catch (DuplicateKeyException e) {
            SubsiteEvent duplicated = findByEventId(req.getEventId());
            if (duplicated != null) {
                return toResp(duplicated);
            }
            throw e;
        }

        if (Boolean.TRUE.equals(req.getNotifyUser()) && StringUtils.hasText(event.getUserId())) {
            notificationService.createNotification(
                    event.getUserId(),
                    NotificationType.SYSTEM,
                    event.getTitle(),
                    event.getContent(),
                    event.getRelatedId(),
                    null
            );
        }

        log.info("[subsite-event] recorded eventId={}, source={}, type={}, userId={}",
                event.getEventId(), event.getSource(), event.getEventType(), event.getUserId());
        ecosystemMetrics.recordSubsiteEvent(event.getSource(), event.getEventType());
        return toResp(event);
    }

    @Override
    public SimplePageResp<SubsiteEventResp> pageMy(String userId, int page, int pageSize, String source) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.TOKEN_MISSING, "请先登录后再查看权益轨迹");
        }
        LambdaQueryWrapper<SubsiteEvent> wrapper = baseWrapper(source, null, null, null);
        wrapper.eq(SubsiteEvent::getUserId, userId);
        return page(wrapper, page, pageSize);
    }

    @Override
    public SimplePageResp<SubsiteEventResp> pageAdmin(int page,
                                                      int pageSize,
                                                      String source,
                                                      String eventType,
                                                      String userId,
                                                      String status) {
        LambdaQueryWrapper<SubsiteEvent> wrapper = baseWrapper(source, eventType, userId, status);
        return page(wrapper, page, pageSize);
    }

    private SimplePageResp<SubsiteEventResp> page(LambdaQueryWrapper<SubsiteEvent> wrapper,
                                                  int page,
                                                  int pageSize) {
        Page<SubsiteEvent> p = new Page<>(Math.max(1, page), Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE));
        Page<SubsiteEvent> result = subsiteEventMapper.selectPage(p, wrapper);
        List<SubsiteEventResp> records = result.getRecords().stream()
                .map(this::toResp)
                .toList();
        return SimplePageResp.of(records, result.getTotal());
    }

    private LambdaQueryWrapper<SubsiteEvent> baseWrapper(String source,
                                                        String eventType,
                                                        String userId,
                                                        String status) {
        LambdaQueryWrapper<SubsiteEvent> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(source)) {
            wrapper.eq(SubsiteEvent::getSource, source.trim());
        }
        if (StringUtils.hasText(eventType)) {
            wrapper.eq(SubsiteEvent::getEventType, eventType.trim());
        }
        if (StringUtils.hasText(userId)) {
            wrapper.eq(SubsiteEvent::getUserId, userId.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SubsiteEvent::getStatus, status.trim());
        }
        wrapper.orderByDesc(SubsiteEvent::getCreatedAt)
                .orderByDesc(SubsiteEvent::getId);
        return wrapper;
    }

    private void validateCaller(SubsiteEventCreateReq req, String serviceId) {
        if (!StringUtils.hasText(serviceId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "仅内部服务可上报子站事件");
        }
        if (!req.getSource().equals(serviceId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION,
                    "source 必须与 X-Service-Id 一致");
        }
    }

    private String serializePayload(SubsiteEventCreateReq req) {
        if (req.getPayload() == null || req.getPayload().isNull()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(req.getPayload());
            if (json.length() > 4000) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "payload 不能超过 4000 字符");
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "payload 不是合法 JSON");
        }
    }

    private SubsiteEvent findByEventId(String eventId) {
        return subsiteEventMapper.selectOne(new LambdaQueryWrapper<SubsiteEvent>()
                .eq(SubsiteEvent::getEventId, eventId)
                .last("LIMIT 1"));
    }

    private String normalizeSeverity(String severity) {
        return StringUtils.hasText(severity) ? severity.trim() : "info";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private SubsiteEventResp toResp(SubsiteEvent event) {
        return SubsiteEventResp.builder()
                .id(event.getId())
                .eventId(event.getEventId())
                .source(event.getSource())
                .eventType(event.getEventType())
                .userId(event.getUserId())
                .title(event.getTitle())
                .content(event.getContent())
                .relatedId(event.getRelatedId())
                .severity(event.getSeverity())
                .status(event.getStatus())
                .payloadJson(event.getPayloadJson())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
