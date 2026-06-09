package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.ReactionResp;
import com.campus.trend.campus_pulse.entity.ContentReaction;
import com.campus.trend.campus_pulse.mapper.ContentReactionMapper;
import com.campus.trend.campus_pulse.service.ReactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReactionServiceImpl extends ServiceImpl<ContentReactionMapper, ContentReaction> implements ReactionService {

    private static final Set<String> VALID_TYPES = Set.of("love", "haha", "wow", "celebrate");
    private static final Set<String> VALID_TARGETS = Set.of("post", "comment");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReactionResp react(String targetType, String targetId, String userId, String type) {
        validateTarget(targetType);
        if (!VALID_TYPES.contains(type)) {
            throw new BusinessException("不支持的表情类型");
        }
        if (userId == null) {
            throw new BusinessException("请先登录");
        }

        ContentReaction existing = lambdaQuery()
                .eq(ContentReaction::getTargetType, targetType)
                .eq(ContentReaction::getTargetId, targetId)
                .eq(ContentReaction::getUserId, userId)
                .one();

        if (existing != null) {
            if (type.equals(existing.getReactionType())) {
                // 同型再点 = 取消
                removeById(existing.getId());
            } else {
                // 切换表情
                existing.setReactionType(type);
                updateById(existing);
            }
        } else {
            ContentReaction record = new ContentReaction()
                    .setTargetType(targetType)
                    .setTargetId(targetId)
                    .setUserId(userId)
                    .setReactionType(type)
                    .setCreateTime(LocalDateTime.now());
            save(record);
        }

        return build(targetType, targetId, userId);
    }

    @Override
    public ReactionResp getReactions(String targetType, String targetId, String userId) {
        validateTarget(targetType);
        return build(targetType, targetId, userId);
    }

    @Override
    public Map<String, ReactionResp> getReactionsBatch(String targetType, List<String> targetIds, String userId) {
        validateTarget(targetType);
        Map<String, ReactionResp> result = new LinkedHashMap<>();
        if (targetIds == null || targetIds.isEmpty()) {
            return result;
        }

        List<ContentReaction> rows = lambdaQuery()
                .eq(ContentReaction::getTargetType, targetType)
                .in(ContentReaction::getTargetId, targetIds)
                .list();

        Map<String, List<ContentReaction>> byTarget = rows.stream()
                .collect(Collectors.groupingBy(ContentReaction::getTargetId));

        for (String id : targetIds) {
            result.put(id, toResp(byTarget.getOrDefault(id, Collections.emptyList()), userId));
        }
        return result;
    }

    private ReactionResp build(String targetType, String targetId, String userId) {
        List<ContentReaction> rows = lambdaQuery()
                .eq(ContentReaction::getTargetType, targetType)
                .eq(ContentReaction::getTargetId, targetId)
                .list();
        return toResp(rows, userId);
    }

    private ReactionResp toResp(List<ContentReaction> rows, String userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        String mine = null;
        for (ContentReaction r : rows) {
            counts.merge(r.getReactionType(), 1L, Long::sum);
            if (userId != null && userId.equals(r.getUserId())) {
                mine = r.getReactionType();
            }
        }
        ReactionResp resp = new ReactionResp();
        resp.setCounts(counts);
        resp.setMine(mine);
        return resp;
    }

    private void validateTarget(String targetType) {
        if (!VALID_TARGETS.contains(targetType)) {
            throw new BusinessException("不支持的目标类型");
        }
    }
}
