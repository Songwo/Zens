package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.entity.SysPostLike;

public interface PostLikeService extends IService<SysPostLike> {

    /**
     * 判断用户是否已点赞该帖子
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-已点赞, false-未点赞
     */
    boolean isLike(String postId, String userId);

    /**
     * 根据用户ID分页获取用户喜欢的帖子
     *
     * @param userId   用户ID
     * @param page     页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页的点赞帖子列表
     */
    IPage<SysPost> getPostLikeWithPage(String userId, int page, int pageSize);

    /**
     * 用户点赞帖子
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-点赞成功, false-已经点赞过
     */
    boolean likePost(String postId, String userId);

    /**
     * 用户取消点赞帖子
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-取消成功, false-未点赞过
     */
    boolean unlikePost(String postId, String userId);

    /**
     * 切换点赞状态（点赞/取消点赞）
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-已点赞, false-已取消点赞
     */
    boolean toggleLike(String postId, String userId);

}
