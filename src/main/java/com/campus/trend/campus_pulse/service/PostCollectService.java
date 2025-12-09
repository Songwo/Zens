package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.entity.SysPostCollect;

public interface PostCollectService extends IService<SysPostCollect> {

    /**
     * 判断用户是否已收藏该帖子
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-已收藏, false-未收藏
     */
    boolean isCollect(String postId, String userId);

    /**
     * 根据用户ID分页获取用户收藏的帖子
     *
     * @param userId   用户ID
     * @param page     页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页的收藏帖子列表
     */
    IPage<SysPost> getPostCollectWithPage(String userId, int page, int pageSize);

    /**
     * 用户收藏帖子
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-收藏成功, false-已经收藏过
     */
    boolean collectPost(String postId, String userId);

    /**
     * 用户取消收藏帖子
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-取消成功, false-未收藏过
     */
    boolean uncollectPost(String postId, String userId);

    /**
     * 切换收藏状态（收藏/取消收藏）
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-已收藏, false-已取消收藏
     */
    boolean toggleCollect(String postId, String userId);

}
