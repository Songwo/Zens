package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.ReactionResp;

import java.util.List;
import java.util.Map;

/**
 * 内容表情反应服务
 */
public interface ReactionService {

    /** 切换/设置表情：同型取消、异型改型、无则新增；返回该目标最新聚合 */
    ReactionResp react(String targetType, String targetId, String userId, String type);

    /** 查询单个目标的表情聚合 */
    ReactionResp getReactions(String targetType, String targetId, String userId);

    /** 批量查询多个目标的表情聚合（评论列表用，避免 N+1） */
    Map<String, ReactionResp> getReactionsBatch(String targetType, List<String> targetIds, String userId);
}
