package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

import java.util.Map;

/**
 * 某个目标(帖子/评论)的表情反应聚合
 */
@Data
public class ReactionResp {

    /** 各表情类型的数量: {love: 3, haha: 1, ...} */
    private Map<String, Long> counts;

    /** 当前用户所选表情类型；未反应为 null */
    private String mine;
}
