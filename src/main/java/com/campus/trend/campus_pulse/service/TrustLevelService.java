package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.TrustInfoResp;

/**
 * 信任等级服务（借鉴 Discourse TL0-TL4 社区自治模型）
 */
public interface TrustLevelService {

    /** 读取用户当前信任等级（未找到返回 0） */
    int getTrustLevel(String userId);

    /** 是否达到指定最低信任等级 */
    boolean isUserTrusted(String userId, int minLevel);

    /** 能否发布外链（tl >= 1） */
    boolean canPostExternalLinks(String userId);

    /** 能否上传附件（tl >= 2） */
    boolean canUploadAttachments(String userId);

    /**
     * 举报 flag 权重：tl>=3 → 5，tl>=2 → 3，否则 → 1
     */
    int flagWeight(String userId);

    /** 获取用户的信任等级详情（含指标、阈值、进度） */
    TrustInfoResp getUserTrustInfo(String userId);

    /** 各信任等级的标签/描述/特权（前端展示用） */
    java.util.List<TrustInfoResp.LevelSpec> getLevelSpecs();

    /**
     * 重算单个用户的信任等级并落库，返回是否发生变更。
     */
    boolean recalculateAndPromote(String userId);

    /**
     * 批量重算所有活跃用户（定时任务调用）。
     */
    int batchRecalculateAllActiveUsers();

    /**
     * 管理员手动设置某用户信任等级（仅 TL4 需手动，其它也可强制）。
     */
    void setTrustLevel(String operatorId, String userId, int newLevel, String reason);
}
