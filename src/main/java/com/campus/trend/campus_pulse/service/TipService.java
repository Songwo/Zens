package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.TipRecord;

import java.util.List;

/**
 * 打赏服务接口
 */
public interface TipService extends IService<TipRecord> {

    /**
     * 打赏
     * @param targetType 目标类型 post/comment
     * @param targetId 目标ID
     * @param amount 打赏积分数
     * @param message 打赏留言
     * @param userId 打赏者ID
     */
    void tip(String targetType, String targetId, Integer amount, String message, String userId);

    /**
     * 获取用户收到的打赏记录
     * @param userId 用户ID
     * @return 打赏记录列表
     */
    List<TipRecord> getReceivedTips(String userId);

    /**
     * 获取用户发出的打赏记录
     * @param userId 用户ID
     * @return 打赏记录列表
     */
    List<TipRecord> getSentTips(String userId);

    /**
     * 获取目标的打赏统计
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @return 打赏总积分
     */
    Integer getTipSum(String targetType, String targetId);
}
