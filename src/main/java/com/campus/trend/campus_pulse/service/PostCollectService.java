package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostCollect;

public interface PostCollectService extends IService<PostCollect> {

    /**
     * Song：判断用户是否已收藏该帖子
     *
     */
    boolean isCollect(String postId, String userId);

    IPage<Post> getPostCollectWithPage(String userId, int page, int pageSize);

    /**
     * Song：用户收藏帖子
     *
     */
    boolean collectPost(String postId, String userId);

    /**
     * Song：用户取消收藏帖子
     *
     */
    boolean uncollectPost(String postId, String userId);

    /**
     * Song：切换收藏状态（收藏/取消收藏）
     *
     */
    boolean toggleCollect(String postId, String userId);

}
