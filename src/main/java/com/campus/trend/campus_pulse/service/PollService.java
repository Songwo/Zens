package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.PollCreateReq;
import com.campus.trend.campus_pulse.dto.request.PollVoteReq;
import com.campus.trend.campus_pulse.dto.response.PollResp;

/**
 * 帖子投票服务。
 * 投票数据独立三表（poll / poll_option / poll_vote），按 post_id 关联，
 * 单独接口读取，不进入帖子详情缓存（票数频繁变动）。
 */
public interface PollService {

    /**
     * 发帖时创建投票（在发帖事务内调用）。
     * 选项做去空白、去重、数量(2~10)校验；单选强制 maxChoices=1。
     *
     * @param postId  已落库的帖子ID
     * @param req     投票创建请求
     * @param userId  创建者（帖子作者）
     */
    void createForPost(String postId, PollCreateReq req, String userId);

    /**
     * 按帖子ID查询投票全貌（含当前用户视角的可投/已投/结果可见状态）。
     * 帖子无投票时返回 null。访客（userId 为 null）也可查看。
     *
     * @param postId         帖子ID
     * @param currentUserId  当前用户ID，未登录传 null
     */
    PollResp getByPostId(String postId, String currentUserId);

    /**
     * 投票。校验：已登录、未截止、未投过、选项归属与数量（单选1个/多选不超上限）。
     * 写入 poll_vote 并刷新选项票数与参与人数冗余计数。
     *
     * @param req     投票请求
     * @param userId  投票用户
     * @return 投票后的最新投票全貌
     */
    PollResp vote(PollVoteReq req, String userId);

    /**
     * 提前关闭投票（作者 / 版主 / 管理员）。关闭后停止投票、对所有人展示结果。
     *
     * @param pollId  投票ID
     * @param userId  操作者
     * @return 关闭后的投票全貌
     */
    PollResp close(Long pollId, String userId);
}
