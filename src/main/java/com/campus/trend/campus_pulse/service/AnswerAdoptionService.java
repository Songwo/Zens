package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.AnswerAdoption;

/**
 * 答案采纳服务接口
 */
public interface AnswerAdoptionService extends IService<AnswerAdoption> {

    /**
     * 采纳答案
     * @param postId 帖子ID
     * @param commentId 评论ID
     * @param userId 采纳者ID（必须是帖子作者）
     */
    void adoptAnswer(String postId, String commentId, String userId);

    /**
     * 取消采纳
     * @param postId 帖子ID
     * @param userId 操作者ID
     */
    void cancelAdoption(String postId, String userId);

    /**
     * 获取帖子的采纳答案
     * @param postId 帖子ID
     * @return 采纳记录，如果没有则返回null
     */
    AnswerAdoption getAdoptedAnswer(String postId);
}
